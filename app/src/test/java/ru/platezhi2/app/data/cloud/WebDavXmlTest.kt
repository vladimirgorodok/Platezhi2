package ru.platezhi2.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Разбор ответов Яндекс Диска по WebDAV. */
class WebDavXmlTest {

    private val response = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:">
          <d:response>
            <d:href>/%D0%9F%D0%BB%D0%B0%D1%82%D0%B5%D0%B6%D0%B82/</d:href>
            <d:propstat>
              <d:prop>
                <d:displayname>Платежи2</d:displayname>
                <d:resourcetype><d:collection/></d:resourcetype>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
          <d:response>
            <d:href>/%D0%9F%D0%BB%D0%B0%D1%82%D0%B5%D0%B6%D0%B82/platezhi2-2026-11-17_142530.json</d:href>
            <d:propstat>
              <d:prop>
                <d:displayname>platezhi2-2026-11-17_142530.json</d:displayname>
                <d:resourcetype/>
                <d:getcontentlength>2048</d:getcontentlength>
                <d:getlastmodified>Tue, 17 Nov 2026 14:25:30 GMT</d:getlastmodified>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun `распознаёт папку и файл`() {
        val entries = WebDavXml.parseMultiStatus(response)

        assertEquals(2, entries.size)

        val folder = entries[0]
        assertEquals("Платежи2", folder.name)
        assertEquals("/Платежи2", folder.path)
        assertTrue(folder.isDirectory)

        val file = entries[1]
        assertEquals("platezhi2-2026-11-17_142530.json", file.name)
        assertEquals("/Платежи2/platezhi2-2026-11-17_142530.json", file.path)
        assertFalse(file.isDirectory)
        assertEquals(2048L, file.size)
        assertTrue(file.modified > 0L)
    }

    @Test
    fun `путь из полного адреса и с плюсом в имени`() {
        assertEquals(
            "/Папка/файл+1.json",
            WebDavXml.pathOf("https://webdav.yandex.ru/%D0%9F%D0%B0%D0%BF%D0%BA%D0%B0/%D1%84%D0%B0%D0%B9%D0%BB+1.json"),
        )
    }

    @Test
    fun `имя берётся из пути, если displayname пустой`() {
        val xml = """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/folder/platezhi2-icons-2026-11-17_142530.zip</d:href>
                <d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entry = WebDavXml.parseMultiStatus(xml).single()
        assertEquals("platezhi2-icons-2026-11-17_142530.zip", entry.name)
        assertFalse(entry.isDirectory)
    }

    @Test
    fun `корневой путь остаётся корневым`() {
        assertEquals("/", WebDavXml.pathOf("/"))
        assertEquals("/", WebDavXml.pathOf("https://webdav.yandex.ru/"))
        assertEquals("/Платежи2", WebDavXml.pathOf("/Платежи2/"))
    }

    @Test
    fun `свободное место читается из ответа`() {
        val xml = """
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:propstat><d:prop>
                <d:quota-available-bytes>12345678901</d:quota-available-bytes>
              </d:prop></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()

        assertEquals(12345678901L, WebDavXml.parseQuota(xml))
    }

    @Test
    fun `путь кодируется и раскодируется обратно`() {
        val path = "/Платежи 2/файл 1.json"
        val encoded = WebDavXml.encodePath(path)

        assertTrue(encoded.startsWith("/%D0%9F"))
        assertTrue(encoded.contains("%20"))
        assertFalse(encoded.contains("Платежи"))

        assertEquals(path, WebDavXml.pathOf(encoded))
    }
}
