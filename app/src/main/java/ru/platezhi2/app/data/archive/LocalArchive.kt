package ru.platezhi2.app.data.archive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.platezhi2.app.data.repo.PaymentRepository
import ru.platezhi2.app.data.transfer.TransferManager

/**
 * Архив данных на устройстве — как «Защита данных» в Автобазе.
 *
 * В архиве лежат и платежи, и значки-картинки, поэтому из него можно вернуть
 * данные целиком, даже без интернета и без Яндекс Диска. Копия обновляется сама
 * при запуске приложения и перед загрузкой из облака (именно она заменяет все
 * данные, поэтому перед ней всегда остаётся свежая копия).
 */
class LocalArchive(
    private val files: ArchiveFiles,
    private val transfer: TransferManager,
    private val repo: PaymentRepository,
    private val scope: CoroutineScope,
) {

    private val mutex = Mutex()

    suspend fun info(): ArchiveFiles.Info? = withContext(Dispatchers.IO) { files.info() }

    suspend fun previousInfo(): ArchiveFiles.Info? = withContext(Dispatchers.IO) { files.previousInfo() }

    /** Обновляет копию в фоне: вызывается при запуске приложения. */
    fun refreshInBackground() {
        scope.launch {
            try {
                create()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Копия — не повод падать: сделаем при следующем удобном случае.
            }
        }
    }

    /**
     * Обновляет архив. false — если сохранять нечего.
     *
     * Без [force] пустые данные в архив не пишем: иначе случайная очистка
     * затрёт последнюю хорошую копию.
     */
    suspend fun create(force: Boolean = false): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!force && repo.getCards().isEmpty()) return@withContext false
            val text = runCatching { transfer.archiveJson() }.getOrNull() ?: return@withContext false
            files.write(text)
        }
    }

    /**
     * Возвращает данные из архива. false — если копии нет.
     *
     * Перед заменой текущее состояние кладём в отдельный файл: сразу после
     * восстановления можно вернуться на шаг назад.
     */
    suspend fun restore(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val text = files.read() ?: return@withContext false
            runCatching { transfer.archiveJson() }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { files.writePrevious(it) }
            transfer.importFromText(text, replaceAll = true)
            true
        }
    }

    /** Возвращает состояние, сохранённое перед последним восстановлением. */
    suspend fun restorePrevious(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val text = files.readPrevious() ?: return@withContext false
            transfer.importFromText(text, replaceAll = true)
            true
        }
    }
}
