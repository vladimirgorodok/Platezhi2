package ru.platezhi2.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.platezhi2.app.data.settings.DEFAULT_CLOUD_FOLDER
import ru.platezhi2.app.data.settings.normalizeCloudFolder
import java.time.LocalDateTime

/** Имена архивов и пути к папке на Яндекс Диске. */
class CloudNamingTest {

    @Test
    fun `имя копии платежей собирается по дате`() {
        val name = CloudSync.snapshotName(LocalDateTime.of(2026, 11, 17, 14, 25, 30))
        assertEquals("platezhi2-2026-11-17_142530.json", name)
        assertTrue(name.startsWith(CloudSync.PREFIX))
        assertTrue(name.endsWith(CloudSync.SUFFIX))
    }

    @Test
    fun `имя архива значков собирается по дате`() {
        val name = CloudSync.iconArchiveName(LocalDateTime.of(2026, 11, 17, 14, 25, 30))
        assertEquals("platezhi2-icons-2026-11-17_142530.zip", name)
    }

    @Test
    fun `имена копий сортируются по свежести`() {
        val older = CloudSync.snapshotName(LocalDateTime.of(2026, 9, 9, 9, 9, 9))
        val newer = CloudSync.snapshotName(LocalDateTime.of(2026, 11, 17, 14, 25, 30))
        val newest = CloudSync.snapshotName(LocalDateTime.of(2027, 1, 1, 0, 0, 0))

        val sorted = listOf(older, newest, newer).sortedDescending()
        assertEquals(listOf(newest, newer, older), sorted)
    }

    @Test
    fun `путь к папке приводится к единому виду`() {
        assertEquals("/Платежи2", normalizeCloudFolder("Платежи2"))
        assertEquals("/Платежи2", normalizeCloudFolder("/Платежи2/"))
        assertEquals("/Платежи2/Внутри", normalizeCloudFolder("//Платежи2//Внутри//"))
        assertEquals("/", normalizeCloudFolder(""))
        assertEquals(DEFAULT_CLOUD_FOLDER, normalizeCloudFolder(DEFAULT_CLOUD_FOLDER))
    }
}
