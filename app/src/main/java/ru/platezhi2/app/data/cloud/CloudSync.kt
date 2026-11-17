package ru.platezhi2.app.data.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import ru.platezhi2.app.data.repo.PaymentRepository
import ru.platezhi2.app.data.settings.SettingsRepository
import ru.platezhi2.app.data.settings.normalizeCloudFolder
import ru.platezhi2.app.data.transfer.TransferManager
import ru.platezhi2.app.media.ImageStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Что получилось при обмене с облаком. */
data class CloudPullResult(val fileName: String, val cards: Int, val icons: Int = 0)

/** Что записалось в облако. */
data class CloudPushResult(
    val dataName: String,
    val iconsName: String?,
    /** Архив значков не ушёл, хотя свои значки есть (не критично: данные уже сохранены). */
    val iconsFailed: Boolean = false,
)

/**
 * Обмен данными с Яндекс Диском.
 *
 * В облачную папку пишутся два архива:
 *  - `platezhi2-<дата_время>.json` — платежи и оплаты (значки внутри);
 *  - `platezhi2-icons-<дата_время>.zip` — отдельная база значков-картинок,
 *    чтобы значки можно было подтянуть на другом устройстве.
 *
 * Хранятся последние [SettingsRepository] копии каждого архива, более старые удаляются.
 */
class CloudSync(
    private val settings: SettingsRepository,
    private val transfer: TransferManager,
    private val repo: PaymentRepository,
    private val images: ImageStore,
) {

    companion object {
        const val PREFIX = "platezhi2-"
        const val SUFFIX = ".json"
        const val ICON_PREFIX = "platezhi2-icons-"
        const val ICON_SUFFIX = ".zip"

        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")

        fun snapshotName(at: LocalDateTime = LocalDateTime.now()): String =
            "$PREFIX${at.format(STAMP)}$SUFFIX"

        fun iconArchiveName(at: LocalDateTime = LocalDateTime.now()): String =
            "$ICON_PREFIX${at.format(STAMP)}$ICON_SUFFIX"
    }

    private suspend fun client(login: String, password: String): YandexDiskClient {
        if (login.isBlank() || password.isBlank()) {
            throw CloudException("Заполните логин Яндекс и пароль приложения")
        }
        return YandexDiskClient(login, password)
    }

    private suspend fun clientFromSettings(): YandexDiskClient {
        val s = settings.settings.first()
        return client(s.yandexLogin, s.yandexPassword)
    }

    private suspend fun folder(): String =
        normalizeCloudFolder(settings.settings.first().cloudFolder)

    /** Проверка логина, пароля и папки (кнопка «Проверить подключение»). */
    suspend fun checkConnection(login: String, password: String, folder: String): String =
        client(login, password).checkAccess(normalizeCloudFolder(folder))

    /** Папки внутри пути — для выбора папки синхронизации. */
    suspend fun listFolders(path: String): List<RemoteEntry> =
        clientFromSettings().list(normalizeCloudFolder(path)).filter { it.isDirectory }

    /** Создаёт папку синхронизации (вместе с родительскими). */
    suspend fun createFolder(path: String) {
        clientFromSettings().createFolderChain(normalizeCloudFolder(path))
    }

    /** Копии платежей в папке, самые свежие — первыми. */
    suspend fun listSnapshots(folder: String? = null): List<RemoteEntry> =
        snapshotsIn(clientFromSettings(), folder?.let(::normalizeCloudFolder) ?: folder())

    /** Архивы значков в папке, самые свежие — первыми. */
    suspend fun listIconArchives(folder: String? = null): List<RemoteEntry> =
        iconArchivesIn(clientFromSettings(), folder?.let(::normalizeCloudFolder) ?: folder())

    /** Копии платежей в папке. Ошибку сети не скрываем: о ней нужно сказать пользователю. */
    private suspend fun snapshotsIn(client: YandexDiskClient, folder: String): List<RemoteEntry> =
        client.list(folder)
            .filter { !it.isDirectory && it.name.startsWith(PREFIX) && it.name.endsWith(SUFFIX, true) }
            .sortedByDescending { it.name }

    /** Архивы значков в папке. */
    private suspend fun iconArchivesIn(client: YandexDiskClient, folder: String): List<RemoteEntry> =
        client.list(folder)
            .filter { !it.isDirectory && it.name.startsWith(ICON_PREFIX) && it.name.endsWith(ICON_SUFFIX, true) }
            .sortedByDescending { it.name }

    // ------------------------------------------------------------------
    // Выгрузка
    // ------------------------------------------------------------------

    /** Сохраняет копию платежей и архив значков. Старые копии сверх лимита удаляются. */
    suspend fun push(): CloudPushResult {
        val current = settings.settings.first()
        val target = normalizeCloudFolder(current.cloudFolder)
        val client = client(current.yandexLogin, current.yandexPassword)
        client.createFolderChain(target)

        val now = LocalDateTime.now()
        val dataName = snapshotName(now)
        uploadChecked(client, "$target/$dataName", transfer.snapshotJson().toByteArray(Charsets.UTF_8))

        // Данные уже сохранены: сбой выгрузки значков не должен выглядеть
        // как «ничего не сохранилось».
        var iconsFailed = false
        val iconsName = try {
            uploadIcons(client, target, now)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            iconsFailed = true
            null
        }

        // Чистка старых копий не должна считаться ошибкой выгрузки.
        val keep = current.cloudKeepCopies.coerceIn(1, 50)
        runCatching { snapshotsIn(client, target).drop(keep).forEach { client.delete(it.path) } }
        runCatching { iconArchivesIn(client, target).drop(keep).forEach { client.delete(it.path) } }

        settings.setCloudSynced(dataName, System.currentTimeMillis())
        return CloudPushResult(dataName, iconsName, iconsFailed)
    }

    /** Отдельно сохраняет архив значков. null — если своих значков нет. */
    suspend fun pushIcons(): String? {
        val current = settings.settings.first()
        val target = normalizeCloudFolder(current.cloudFolder)
        val client = client(current.yandexLogin, current.yandexPassword)
        client.createFolderChain(target)
        val name = uploadIcons(client, target, LocalDateTime.now())
        val keep = current.cloudKeepCopies.coerceIn(1, 50)
        runCatching { iconArchivesIn(client, target).drop(keep).forEach { client.delete(it.path) } }
        return name
    }

    private suspend fun uploadIcons(
        client: YandexDiskClient,
        folder: String,
        at: LocalDateTime,
    ): String? {
        val bytes = IconArchive.build(repo, images) ?: return null
        val name = iconArchiveName(at)
        uploadChecked(client, "$folder/$name", bytes)
        return name
    }

    /**
     * Выгружает файл и проверяет результат.
     *
     * На мобильном интернете бывает так: файл на Диск записался, а ответ до
     * приложения не дошёл. Тогда это не ошибка — сравниваем размер файла.
     */
    private suspend fun uploadChecked(client: YandexDiskClient, path: String, bytes: ByteArray) {
        try {
            client.upload(path, bytes)
            return
        } catch (error: CloudException) {
            val remoteSize = runCatching { client.remoteSize(path) }.getOrNull()
            if (remoteSize == bytes.size.toLong()) return
        }
        // Вторая попытка: если и она не удастся, ошибка уйдёт наверх.
        client.upload(path, bytes)
    }

    // ------------------------------------------------------------------
    // Загрузка
    // ------------------------------------------------------------------

    /** Загружает свежую копию платежей и подтягивает значки из архива. */
    suspend fun pull(): CloudPullResult {
        val current = settings.settings.first()
        val target = normalizeCloudFolder(current.cloudFolder)
        val client = client(current.yandexLogin, current.yandexPassword)

        val newest = snapshotsIn(client, target).firstOrNull()
            ?: throw CloudException("В папке «$target» нет файлов синхронизации")
        val bytes = client.download(newest.path)
        val count = transfer.importFromText(bytes.toString(Charsets.UTF_8), replaceAll = true)

        // Значки: подтягиваем из архива значков — для тех платежей, где картинки нет.
        val icons = try {
            restoreIcons(client, target, onlyMissing = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            0
        }

        settings.setCloudSynced(newest.name, System.currentTimeMillis())
        return CloudPullResult(newest.name, count, icons)
    }

    /** Восстанавливает значки из свежего архива. Возвращает число подтянутых картинок. */
    suspend fun pullIcons(onlyMissing: Boolean = true): Int {
        val current = settings.settings.first()
        val target = normalizeCloudFolder(current.cloudFolder)
        val client = client(current.yandexLogin, current.yandexPassword)
        val restored = restoreIcons(client, target, onlyMissing)
        if (restored == 0 && onlyMissing) {
            val hasArchive = iconArchivesIn(client, target).isNotEmpty()
            if (!hasArchive) throw CloudException("В папке «$target» нет архива значков")
        }
        return restored
    }

    private suspend fun restoreIcons(
        client: YandexDiskClient,
        folder: String,
        onlyMissing: Boolean,
    ): Int {
        val archive = iconArchivesIn(client, folder).firstOrNull() ?: return 0
        val entries = IconArchive.parse(client.download(archive.path))
        if (entries.isEmpty()) return 0

        val byTitle = entries.groupBy { it.title.trim().lowercase(Locale.ROOT) }
        var restored = 0
        repo.getCards().forEach { card ->
            if (onlyMissing && images.exists(card.imagePath)) return@forEach
            val entry = byTitle[card.title.trim().lowercase(Locale.ROOT)]?.firstOrNull() ?: return@forEach
            val path = images.saveBytes(entry.bytes) ?: return@forEach
            repo.saveCard(card.copy(imagePath = path))
            restored++
        }
        return restored
    }

    /** Имя самой свежей копии платежей (null — копий нет). */
    suspend fun newestSnapshotName(): String? = listSnapshots().firstOrNull()?.name

    /** Имя самого свежего архива значков (null — архива нет). */
    suspend fun newestIconArchiveName(): String? = listIconArchives().firstOrNull()?.name
}
