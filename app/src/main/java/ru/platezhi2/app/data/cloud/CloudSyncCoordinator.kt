package ru.platezhi2.app.data.cloud

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.platezhi2.app.data.archive.LocalArchive
import ru.platezhi2.app.data.repo.PaymentRepository
import ru.platezhi2.app.data.settings.AppSettings
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
    private val archive: LocalArchive,
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
                writes.value = writes.value + 1
            }
        }
        // Ждём «тишины» после правок и только потом выгружаем: уже начатая
        // выгрузка не отменяется новыми изменениями.
        scope.launch {
            var handled = 0L
            while (true) {
                val stamp = writes.first { it != 0L && it != handled }
                handled = stamp
                delay(WRITE_DELAY_MS)
                if (writes.value != handled) continue
                pushQuietly()
            }
        }
        scope.launch { syncOnStart() }
    }

    /** Вызывается, когда приложение уходит в фон. */
    fun onAppStop() {
        scope.launch { pushQuietly() }
    }

    /**
     * Вызывается, когда приложение снова на экране. Только выгружаем свои правки:
     * данные на экране не подменяются (импорт из облака — при запуске приложения).
     */
    fun onAppForeground() {
        scope.launch { pushQuietly() }
    }

    // ------------------------------------------------------------------
    // Действия пользователя
    // ------------------------------------------------------------------

    /** Запускает выгрузку в общем (приложенческом) скоупе: уход с экрана её не отменит. */
    fun pushInBackground() {
        scope.launch { manualPush() }
    }

    /** Запускает загрузку из облака в общем скоупе. */
    fun pullInBackground() {
        scope.launch { manualPull() }
    }

    fun iconsPushInBackground() {
        scope.launch { manualIconsPush() }
    }

    fun iconsRestoreInBackground() {
        scope.launch { manualIconsRestore() }
    }

    suspend fun manualPush(): Result<CloudPushResult> {
        _status.value = Status(busy = true)
        val result = attempt { pushInternal() }
        _status.value = Status(
            busy = false,
            message = result.fold(
                onSuccess = { pushed ->
                    val icons = when {
                        pushed.iconsName != null -> " и архив значков"
                        pushed.iconsFailed -> " (архив значков не ушёл — нажмите «Сохранить»)"
                        else -> ""
                    }
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
        val result = attempt { mutex.withLock { sync.pushIcons() } }
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
        val result = attempt { mutex.withLock { sync.pullIcons(onlyMissing = true) } }
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
        val result = attempt { pullInternal() }
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

    /** Проверка подключения из интерфейса (не отменяется при уходе с экрана). */
    fun checkInBackground(login: String, password: String, folder: String) {
        scope.launch { checkConnection(login, password, folder) }
    }

    suspend fun checkConnection(login: String, password: String, folder: String): Result<String> {
        _status.value = Status(busy = true)
        val result = attempt { sync.checkConnection(login, password, folder) }
        _status.value = Status(
            busy = false,
            message = result.getOrElse { it.message ?: "Не удалось подключиться" },
            failed = result.isFailure,
        )
        return result
    }

    suspend fun loadFolders(path: String): Result<List<RemoteEntry>> =
        attempt { sync.listFolders(path) }

    suspend fun loadSnapshots(): Result<List<RemoteEntry>> = attempt { sync.listSnapshots() }

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
            val cloudHasCopies = attempt { sync.newestSnapshotName() }.getOrNull() != null
            if (repo.getCards().isEmpty() && cloudHasCopies) {
                attempt { pullInternal() }.onFailure { reportFailure(it) }
            } else {
                attempt { pushInternal() }.onFailure { reportFailure(it) }
            }
            return
        }

        val newest = attempt { sync.newestSnapshotName() }.getOrElse { error ->
            reportFailure(error)
            return
        }
        if (newest == current.cloudLastFileName) {
            // Данные не менялись, но архив значков мог обновиться отдельно.
            restoreIconsIfChanged(current)
            return
        }
        if (newest == null) {
            // В облаке ещё нет копий — выгружаем свои данные.
            attempt { pushInternal() }.onFailure { reportFailure(it) }
            return
        }
        attempt { pullInternal() }.onFailure { reportFailure(it) }
    }

    /** Подтягивает значки, если в облаке появился новый архив, а картинок не хватает. */
    private suspend fun restoreIconsIfChanged(current: AppSettings) {
        val latest = attempt { sync.newestIconArchiveName() }.getOrNull() ?: return
        if (latest == current.cloudLastIconsName) return
        if (!sync.hasMissingIcons()) return
        attempt { sync.pullIcons(onlyMissing = true) }.onFailure { reportFailure(it) }
    }

    private suspend fun pushQuietly() {
        val current = settings.settings.first()
        if (!current.autoSync || !current.cloudConfigured || !current.cloudDirty) return
        attempt { pushInternal() }.onFailure { reportFailure(it) }
    }

    private suspend fun pushInternal(): CloudPushResult = mutex.withLock {
        val current = settings.settings.first()
        if (!current.cloudConfigured) throw CloudException("Заполните логин Яндекс и пароль приложения")
        sync.push()
    }

    private suspend fun pullInternal(): CloudPullResult = mutex.withLock {
        // Загрузка из облака заменяет все данные, поэтому сначала оставляем
        // копию на устройстве: из неё всё можно вернуть.
        try {
            archive.create()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Копия не должна мешать обмену с облаком.
        }
        // Изменения, которые возникнут при импорте, — это эхо, а не правки пользователя.
        ignoreWritesUntil = System.currentTimeMillis() + IGNORE_WRITES_MS
        val result = sync.pull()
        settings.setCloudDirty(false)
        Reminders.rescheduleAll(context, repo.getCards(), repo.getPayments())
        result
    }

    private fun reportFailure(error: Throwable) {
        if (error is CancellationException) return
        _status.value = Status(
            busy = false,
            message = friendlyMessage(error),
            failed = true,
        )
    }

    private fun friendlyMessage(error: Throwable): String = when {
        error is CloudException -> error.message ?: "Ошибка Яндекс Диска"
        else -> error.message ?: "Ошибка синхронизации с Яндекс Диском"
    }

    /** Как runCatching, но отмена корутины не превращается в «ошибку». */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
