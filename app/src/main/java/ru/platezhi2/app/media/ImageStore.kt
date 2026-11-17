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
            val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching null
            // Храним уменьшенную копию: в списке значок мелкий, а синхронизация легче.
            val bytes = ImageScaling.shrink(raw) ?: raw
            val target = File(dir, "icon_${System.currentTimeMillis()}.img")
            target.outputStream().use { it.write(bytes) }
            target.absolutePath
        }.getOrNull()
    }

    /**
     * Сохраняет готовые байты картинки (например, распакованные из облачной копии)
     * и возвращает путь к файлу.
     */
    suspend fun saveBytes(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(dir, "icon_${System.currentTimeMillis()}_${bytes.size}.img")
            target.outputStream().use { it.write(bytes) }
            target.absolutePath
        }.getOrNull()
    }

    /** Читает байты картинки по пути из хранилища. Возвращает null, если файла нет. */
    suspend fun readBytes(path: String?): ByteArray? = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext null
        runCatching { File(path).takeIf { it.isFile }?.readBytes() }.getOrNull()
    }

    /** Есть ли картинка по указанному пути на этом устройстве. */
    suspend fun exists(path: String?): Boolean = withContext(Dispatchers.IO) {
        !path.isNullOrBlank() && File(path).isFile
    }
}
