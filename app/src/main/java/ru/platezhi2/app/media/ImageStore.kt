package ru.platezhi2.app.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Хранилище картинок-иконок, выбранных пользователем из файла. */
class ImageStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "icons").apply { if (!exists()) mkdirs() }

    /** Копирует выбранное изображение во внутреннее хранилище и возвращает путь. */
    suspend fun importImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(dir, "icon_${System.currentTimeMillis()}.img")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            target.absolutePath
        }.getOrNull()
    }
}
