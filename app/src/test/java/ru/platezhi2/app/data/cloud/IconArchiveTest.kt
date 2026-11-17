package ru.platezhi2.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Разбор архива значков. */
class IconArchiveTest {

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val manifest = """
        {"version":1,"icons":[
          {"title":"Совкомбанк","file":"icons/0.img","size":4},
          {"title":"Халва","file":"icons/1.img","size":3}
        ]}
    """.trimIndent()

    @Test
    fun `разбирает значки и названия`() {
        val archive = zip(
            IconArchive.MANIFEST to manifest.toByteArray(),
            "icons/0.img" to byteArrayOf(1, 2, 3, 4),
            "icons/1.img" to byteArrayOf(9, 9, 9),
        )

        val entries = IconArchive.parse(archive)

        assertEquals(2, entries.size)
        assertEquals("Совкомбанк", entries[0].title)
        assertEquals("0.img", entries[0].fileName)
        assertTrue(entries[0].bytes.contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertEquals("Халва", entries[1].title)
        assertTrue(entries[1].bytes.contentEquals(byteArrayOf(9, 9, 9)))
    }

    @Test
    fun `пропускает записи без файла и битый архив`() {
        val archive = zip(
            IconArchive.MANIFEST to """
                {"version":1,"icons":[{"title":"Нет файла","file":"icons/7.img"}]}
            """.trimIndent().toByteArray(),
        )

        assertTrue(IconArchive.parse(archive).isEmpty())
        assertTrue(IconArchive.parse(byteArrayOf(1, 2, 3)).isEmpty())
    }

    @Test
    fun `читает подсказки для сопоставления`() {
        val archive = zip(
            IconArchive.MANIFEST to """
                {"version":1,"icons":[
                  {"title":"Совкомбанк","dueDay":22,"amountMinor":150000,"file":"icons/0.img","size":1},
                  {"title":"Халва","file":"icons/1.img","size":1}
                ]}
            """.trimIndent().toByteArray(),
            "icons/0.img" to byteArrayOf(7),
            "icons/1.img" to byteArrayOf(8),
        )

        val entries = IconArchive.parse(archive)

        assertEquals(22, entries[0].dueDay)
        assertEquals(150000L, entries[0].amountMinor)
        // Старый архив без подсказок читается как раньше.
        assertEquals(0, entries[1].dueDay)
        assertEquals(0L, entries[1].amountMinor)
    }

    @Test
    fun `архив без манифеста пуст`() {
        val archive = zip("icons/0.img" to byteArrayOf(1, 2, 3))
        assertTrue(IconArchive.parse(archive).isEmpty())
    }
}
