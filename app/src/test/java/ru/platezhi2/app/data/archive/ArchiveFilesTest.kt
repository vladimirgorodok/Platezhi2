package ru.platezhi2.app.data.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Файлы архива на устройстве: запись, чтение и состояние «до восстановления». */
class ArchiveFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun files() = ArchiveFiles(tmp.root)

    @Test
    fun `копии нет — сведений нет`() {
        val files = files()

        assertNull(files.info())
        assertNull(files.read())
        assertNull(files.readPrevious())
        assertFalse(files.exists())
    }

    @Test
    fun `создаёт папку и пишет копию`() {
        val files = ArchiveFiles(File(tmp.root, "archive"))

        assertTrue(files.write("""{"cards":[]}"""))

        assertEquals("""{"cards":[]}""", files.read())
        assertTrue(files.exists())
        assertTrue(files.info()!!.bytes > 0)
    }

    @Test
    fun `новая копия заменяет старую и не оставляет временных файлов`() {
        val files = files()

        files.write("первая")
        files.write("вторая")

        assertEquals("вторая", files.read())
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `состояние до восстановления хранится отдельно`() {
        val files = files()

        files.write("архив")
        files.writePrevious("до восстановления")

        assertEquals("архив", files.read())
        assertEquals("до восстановления", files.readPrevious())
        assertTrue(files.previousInfo() != null)
        assertTrue(files.info() != null)
    }

    @Test
    fun `пустой текст копией не считается`() {
        val files = files()

        files.write("   ")

        assertNull(files.read())
    }
}
