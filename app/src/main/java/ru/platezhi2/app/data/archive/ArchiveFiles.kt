package ru.platezhi2.app.data.archive

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Файлы архива на устройстве.
 *
 * Отдельный класс без зависимостей от Android, чтобы логику можно было покрыть тестами.
 * Данные лежат в приватной папке приложения, поэтому случайно удалить копию из файлового
 * менеджера нельзя.
 */
class ArchiveFiles(private val dir: File) {

    /** Сведения о копии: когда сделана и сколько занимает. */
    data class Info(val at: Long, val bytes: Long)

    private val archive: File get() = File(dir, ARCHIVE_NAME)
    private val previous: File get() = File(dir, PREVIOUS_NAME)

    /** Копия, которую видит пользователь. */
    fun info(): Info? = infoOf(archive)

    /** Состояние, сохранённое перед предыдущим восстановлением. */
    fun previousInfo(): Info? = infoOf(previous)

    fun exists(): Boolean = archive.isFile

    /**
     * Записывает копию: сначала во временный файл, потом подменяет им старый.
     * Прерванная запись не испортит уже сохранённую копию.
     * false — если записать не удалось.
     */
    fun write(text: String): Boolean {
        return runCatching {
            if (!dir.isDirectory && !dir.mkdirs()) return@runCatching false
            val tmp = File(dir, "archive-${System.nanoTime()}.tmp")
            tmp.writeText(text, StandardCharsets.UTF_8)
            if (tmp.renameTo(archive)) {
                true
            } else {
                // Очень редкий случай: подмена файла не удалась — копируем содержимое вручную.
                archive.writeText(text, StandardCharsets.UTF_8)
                tmp.delete()
                true
            }
        }.getOrDefault(false)
    }

    fun read(): String? = runCatching {
        archive.takeIf { it.isFile }?.readText(StandardCharsets.UTF_8)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Сохраняет текущее состояние перед восстановлением, чтобы был шаг назад. */
    fun writePrevious(text: String): Boolean = runCatching {
        if (!dir.isDirectory) dir.mkdirs()
        previous.writeText(text, StandardCharsets.UTF_8)
        true
    }.getOrDefault(false)

    fun readPrevious(): String? = runCatching {
        previous.takeIf { it.isFile }?.readText(StandardCharsets.UTF_8)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun infoOf(file: File): Info? =
        if (file.isFile) Info(at = file.lastModified(), bytes = file.length()) else null

    companion object {
        const val ARCHIVE_NAME = "platezhi2-archive.json"
        const val PREVIOUS_NAME = "platezhi2-before-restore.json"
    }
}
