package ru.platezhi2.app.logos

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.absoluteValue

/** Спецификация логотипа, выводимая из строки-пути. */
sealed interface LogoSpec {
    data class Image(val path: String) : LogoSpec
    data class Monogram(val text: String, val color: Long) : LogoSpec
    data object None : LogoSpec

    companion object {
        fun parse(path: String?): LogoSpec {
            if (path.isNullOrBlank()) return None
            if (path.startsWith(MONOGRAM_PREFIX)) {
                val rest = path.removePrefix(MONOGRAM_PREFIX)
                val parts = rest.split(":")
                val text = parts.getOrNull(0).orEmpty().take(2)
                val color = parts.getOrNull(1)?.toLongOrNull(16) ?: 0xFF6C5CE7
                return Monogram(text.ifBlank { "?" }, color)
            }
            return Image(path)
        }
    }
}

const val MONOGRAM_PREFIX = "monogram:"

/**
 * Логотипы: импорт из файла/галереи, офлайн-монограмма и онлайн-поиск
 * (Wikimedia Commons). Без интернета всегда доступна монограмма.
 */
class LogoRepository(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "logos").apply { if (!exists()) mkdirs() }

    /** Копирует выбранное изображение во внутреннее хранилище и возвращает путь. */
    suspend fun importImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(dir, "logo_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            target.absolutePath
        }.getOrNull()
    }

    /** Офлайн-результат: монограмма по названию с детерминированным цветом. */
    fun monogram(name: String): String {
        val text = name.trim().firstOrNull()?.uppercase() ?: "?"
        val palette = listOf(0xFF6C5CE7, 0xFF00B894, 0xFFE17055, 0xFF4A90D9, 0xFFD63384, 0xFFB2652A)
        val color = palette[(name.trim().lowercase().hashCode().absoluteValue) % palette.size]
        return "$MONOGRAM_PREFIX$text:${color.toString(16)}"
    }

    /**
     * Онлайн-поиск логотипа по названию платежа. Сначала пробуем подобрать
     * английское название по словарю брендов, затем ищем картинку на Wikimedia.
     */
    suspend fun searchOnline(name: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val query = LogoCatalog.searchQuery(name)
            val fileUrl = searchWikimedia(query) ?: return@runCatching null
            val bytes = httpGet(fileUrl) ?: return@runCatching null
            if (!isPng(bytes)) return@runCatching null
            val target = File(dir, "online_${System.currentTimeMillis()}.png")
            target.writeBytes(bytes)
            target.absolutePath
        }.getOrNull()
    }

    private fun searchWikimedia(query: String): String? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val api = "https://commons.wikimedia.org/w/api.php?action=query&generator=search" +
            "&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=8&prop=imageinfo" +
            "&iiprop=url&iiurlwidth=256&format=json"
        val body = httpGet(api)?.toString(Charsets.UTF_8) ?: return null
        val root = JSONObject(body)
        val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return null
        val keys = pages.keys()
        while (keys.hasNext()) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val url = info.optString("thumburl").ifBlank { info.optString("url") }
            if (url.isNotBlank()) return url
        }
        return null
    }

    private fun httpGet(url: String): ByteArray? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Platezhi2/1.0 (Android; personal app)")
            connectTimeout = 12_000
            readTimeout = 12_000
        }
        return try {
            if (conn.responseCode != 200) return null
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        return sig.indices.all { bytes[it] == sig[it] }
    }
}
