package ru.platezhi2.app.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.platezhi2.app.data.repo.PaymentRepository
import ru.platezhi2.app.media.ImageScaling
import ru.platezhi2.app.media.ImageStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Один значок внутри архива иконок. */
data class IconEntry(
    val title: String,
    val fileName: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is IconEntry && other.title == title && other.fileName == fileName

    override fun hashCode(): Int = title.hashCode() * 31 + fileName.hashCode()
}

/**
 * Отдельный архив с базой значков-картинок.
 *
 * Внутри — zip-файл: `icons.json` со списком значков и сами картинки.
 * Значки сопоставляются с платежами по названию, поэтому архив можно
 * распаковать на другом устройстве даже без файла данных.
 */
object IconArchive {

    const val MANIFEST = "icons.json"
    private const val DIR = "icons/"
    private const val MAX_IMAGE_BYTES = 3 * 1024 * 1024

    /** Собирает архив значков. null — если своих картинок нет. */
    suspend fun build(repo: PaymentRepository, images: ImageStore): ByteArray? =
        withContext(Dispatchers.IO) {
            val collected = ArrayList<Pair<String, ByteArray>>()
            repo.getCards().forEach { card ->
                val raw = images.readBytes(card.imagePath) ?: return@forEach
                val bytes = ImageScaling.shrink(raw) ?: raw
                if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return@forEach
                collected += card.title to bytes
            }
            if (collected.isEmpty()) return@withContext null

            val manifest = JSONObject().apply {
                put("version", 1)
                put(
                    "icons",
                    JSONArray().apply {
                        collected.forEachIndexed { index, (title, bytes) ->
                            put(
                                JSONObject().apply {
                                    put("title", title)
                                    put("file", "$DIR$index.img")
                                    put("size", bytes.size)
                                },
                            )
                        }
                    },
                )
            }

            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                collected.forEachIndexed { index, (_, bytes) ->
                    zip.putNextEntry(ZipEntry("$DIR$index.img"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            out.toByteArray()
        }

    /** Разбирает архив значков. */
    fun parse(bytes: ByteArray): List<IconEntry> {
        val files = HashMap<String, ByteArray>()
        var manifest: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val data = zip.readBytes()
                    if (entry.name.equals(MANIFEST, true)) {
                        manifest = data.toString(Charsets.UTF_8)
                    } else {
                        files[entry.name] = data
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val array = manifest?.let { runCatching { JSONObject(it).optJSONArray("icons") }.getOrNull() }
            ?: return emptyList()

        val result = ArrayList<IconEntry>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val file = o.optString("file")
            val data = files[file] ?: continue
            result += IconEntry(
                title = o.optString("title"),
                fileName = file.substringAfterLast('/'),
                bytes = data,
            )
        }
        return result
    }
}
