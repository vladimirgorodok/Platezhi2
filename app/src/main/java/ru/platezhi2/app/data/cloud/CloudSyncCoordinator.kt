package ru.platezhi2.app.data.cloud

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.platezhi2.app.data.repo.PaymentRepository
import ru.platezhi2.app.data.settings.SettingsRepository
import ru.platezhi2.app.reminders.Reminders

/**
 * Автосинхронизация с Яндекс Диском.
 *
 * Порядок обмена (в облаке всегда одна «свежая» копия):
 *  - при запуске: если есть невыгруженные изменения (в том числе сделанные
 *    без интернета) — сначала выгружаем их, иначе забираем свежую копию;
 *  - после изменений: через несколько секунд выгружаем новую копию;
 *  - при закрытии приложения: выгружаем, если есть что.
 */
class CloudSyncCoordinator(
    private val context: Context,
    private val settings: SettingsRepository,
    private val repo: PaymentRepository,
    private val sync: CloudSync,
    private val scope: CoroutineScope,
) {

    data class Status(
        val busy: Boolean = false,
        val message: String? = null,
        val failed: Boolean = false,
    )

    companion object {
        /** Пауза после изменения данных перед выгрузкой (секунды). */
        private const val WRITE_DELAY_MS = 8_000L

        /** Окно, в котором изменения считаются эхом импорта из облака. */
        private const val IGNORE_WRITES_MS = 3_000L
    }

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val writes = MutableStateFlow(0L)
    private val mutex = Mutex()

    @Volatile
    private var ignoreWritesUntil = 0L

    /** Запускается один раз при старте приложения. */
    fun start() {
        scope.launch {
            repo.changes.collect {
                if (System.currentTimeMillis() < ignoreWritesUntil) return@collect
                settings.setCloudDirty(true)
                writes.value = System.currentTimeMillis()
            }
        }
        scope.launch {
            writes.collectLatest { stamp ->
                if (stamp == 0L) return@collectLatest
                delay(WRITE_DELAY_MS)
                pushQuietly()
            }
        }
        scope.launch { syncOnStart() }
    }

    /** Вызывается, когда приложение уходит в фон. */
    fun onAppStop() {
        scope.launch { pushQuietly() }
    }

    // ------------------------------------------------------------------
    // Действия пользователя
    // ------------------------------------------------------------------

    suspend fun manualPush(): Result<CloudPushResult> {
        _status.value = Status(busy = true)
        val result = runCatching { pushInternal() }
        _status.value = Status(
            busy = false,
            message = result.fold(
                onSuccess = { pushed ->
                    val icons = if (pushed.iconsName != null) " и архив значков" else ""
                    "Сохранено в облако: ${pushed.dataName}$icons"
                },
                onFailure = { it.message ?: "Не удалось сохранить копию" },
            ),
            failed = result.isFailure,
        )
        return result
    }

    /** Отдельно выгрузить базу значков. */
    suspend fun manualIconsPush(): Result<String?> {
        _status.value = Status(busy = true)
        val result = runCatching { mutex.withLock { sync.pushIcons() } }
        _status.value = Status(
            busy = false,
            message = result.fold(
                onSuccess = { name -> if (name != null) "Значки сохранены: $name" else "Своих значков-картинок нет" },
                onFailure = { it.message ?: "Не удалось сохранить значки" },
            ),
            failed = result.isFailure,
        )
        return result
    }

    /** Отдельно подтянуть значки из облака. */
    suspend fun manualIconsRestore(): Result<Int> {
        _status.value = Status(busy = true)
        val result = runCatching { mutex.withLock { sync.pullIcons(onlyMissing = true) } }
        _status.value = Status(
            busy = false,
            message = result.fold(
                onSuccess = { count ->
                    if (count > 0) "Подтянуто значков: $count" else "Все значки уже на месте"
                },
                onFailure = { it.message ?: "Не удалось подтянуть значки" },
            ),
            failed = result.isFailure,
        )
        return result
    }

    suspend fun manualPull(): Result<CloudPullResult> {
        _status.value = Status(busy = true)
        val result = runCatching { pullInternal() }
        _status.value = Status(
            busy = false,
            message = result.fold(
                onSuccess = { pulled ->
                    "Загружено из облака: ${pulled.fileName} (платежей: ${pulled.cards}, значков: ${pulled.icons})"
                },
                onFailure = { it.message ?: "Не удалось загрузить данные" },
            ),
            failed = result.isFailure,
        )
        return result
    }

    suspend fun checkConnection(login: String, password: String, folder: String): Result<String> {
        _status.value = Status(busy = true)
        val result = runCatching { sync.checkConnection(login, password, folder) }
        _status.value = Status(
            busy = false,
            message = result.getOrElse { it.message ?: "Не удалось подключиться" },
            failed = result.isFailure,
        )
        return result
    }

    suspend fun loadFolders(path: String): Result<List<RemoteEntry>> =
        runCatching { sync.listFolders(path) }

    suspend fun loadSnapshots(): Result<List<RemoteEntry>> = runCatching { sync.listSnapshots() }

    suspend fun clearMessage() {
        _status.value = _status.value.copy(message = null)
    }

    // ------------------------------------------------------------------
    // Внутренняя логика
    // ------------------------------------------------------------------

    private suspend fun syncOnStart() {
        val current = settings.settings.first()
        if (!current.autoSync || !current.cloudConfigured) return

        if (current.cloudDirty) {
            // Страховка для нового устройства: пустые локальные данные не выгружаем,
            // иначе можно затереть облако, где уже лежат платежи с другого устройства.
            val cloudHasCopies = runCatching { sync.newestSnapshotName() }.getOrNull() != null
            if (repo.getCards().isEmpty() && cloudHasCopies) {
                runCatching { pullInternal() }.onFailure { reportFailure(it) }
                return
            }
            runCatching { pushInternal() }.onFailure { reportFailure(it) }
            return
        }
        val newest = runCatching { sync.newestSnapshotName() }.getOrNull() ?: return
        if (newest == current.cloudLastFileName) return
        runCatching { pullInternal() }.onFailure { reportFailure(it) }
    }

    private suspend fun pushQuietly() {
        val current = settings.settings.first()
        if (!current.autoSync || !current.cloudConfigured || !current.cloudDirty) return
        runCatching { pushInternal() }.onFailure { reportFailure(it) }
    }

    private suspend fun pushInternal(): CloudPushResult = mutex.withLock {
        val current = settings.settings.first()
        if (!current.cloudConfigured) throw CloudException("Заполните логин Яндекс и пароль приложения")
        sync.push()
    }

    private suspend fun pullInternal(): CloudPullResult = mutex.withLock {
        // Изменения, которые возникнут при импорте, — это эхо, а не правки пользователя.
        ignoreWritesUntil = System.currentTimeMillis() + IGNORE_WRITES_MS
        val result = sync.pull()
        settings.setCloudDirty(false)
        Reminders.rescheduleAll(context, repo.getCards(), repo.getPayments())
        result
    }

    private fun reportFailure(error: Throwable) {
        _status.value = Status(
            busy = false,
            message = error.message ?: "Ошибка синхронизации с Яндекс Диском",
            failed = true,
        )
    }
}
