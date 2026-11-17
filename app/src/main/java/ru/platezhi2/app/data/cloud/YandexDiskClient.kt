package ru.platezhi2.app.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Ошибка обращения к Яндекс Диску с текстом, понятным пользователю. */
class CloudException(message: String, val code: Int = 0) : Exception(message)

/** Элемент на Яндекс Диске: папка или файл. */
data class RemoteEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val modified: Long = 0L,
)

/**
 * Клиент Яндекс Диска по протоколу WebDAV.
 *
 * Авторизация — логин и «пароль приложения» с типом
 * «WebDAV-клиент для Яндекс Диска». Регистрация приложения не нужна.
 */
class YandexDiskClient(
    private val login: String,
    private val password: String,
) {

    companion object {
        const val HOST = "https://webdav.yandex.ru"
        private const val USER_AGENT = "Platezhi2/1.12 (Android)"
        private const val XML = "application/xml; charset=utf-8"

        private val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:displayname/>
                <d:resourcetype/>
                <d:getcontentlength/>
                <d:getlastmodified/>
                <d:quota-available-bytes/>
              </d:prop>
            </d:propfind>
        """.trimIndent()

        /** Человекочитаемый размер. */
        fun humanSize(bytes: Long): String = when {
            bytes >= 1_073_741_824L -> "%.1f ГБ".format(Locale.ROOT, bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> "%.0f МБ".format(Locale.ROOT, bytes / 1_048_576.0)
            bytes >= 1024L -> "%.0f КБ".format(Locale.ROOT, bytes / 1024.0)
            else -> "$bytes Б"
        }
    }

    private data class HttpResult(val code: Int, val body: ByteArray, val location: String?)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // Перенаправления обрабатываем сами: OkHttp меняет метод на GET,
        // а для WebDAV важно сохранить PROPFIND/PUT/DELETE.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val authHeader: String = Credentials.basic(login, password)

    // ------------------------------------------------------------------
    // Операции
    // ------------------------------------------------------------------

    /** Проверяет доступ и заодно создаёт папку синхронизации. */
    suspend fun checkAccess(folder: String): String {
        val res = perform("PROPFIND", "/", body = PROPFIND_BODY.toByteArray(), depth = 0)
        ensureSuccess(res, "Проверка подключения")
        createFolderChain(folder)
        val free = WebDavXml.parseQuota(res.body.toString(Charsets.UTF_8))
        return if (free > 0) {
            "Подключение выполнено. Свободно на Диске: ${humanSize(free)}"
        } else {
            "Подключение выполнено"
        }
    }

    /** Содержимое папки (без самой папки). */
    suspend fun list(path: String): List<RemoteEntry> {
        val target = normalize(path)
        val res = perform("PROPFIND", target, body = PROPFIND_BODY.toByteArray(), depth = 1)
        ensureSuccess(res, "Чтение папки")
        return WebDavXml.parseMultiStatus(res.body.toString(Charsets.UTF_8))
            .filter { it.path != target }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    /** Создаёт папку. Уже существующая папка ошибкой не считается. */
    suspend fun createFolder(path: String) {
        val res = perform("MKCOL", normalize(path))
        if (res.code in 200..299 || res.code == 405) return
        throw CloudException(messageFor(res.code, "Создание папки"), res.code)
    }

    /** Создаёт папку вместе со всеми родительскими. */
    suspend fun createFolderChain(path: String) {
        val target = normalize(path)
        if (target == "/") return
        var current = ""
        target.split('/').filter { it.isNotBlank() }.forEach { segment ->
            current += "/$segment"
            createFolder(current)
        }
    }

    suspend fun upload(path: String, bytes: ByteArray) {
        val res = perform("PUT", normalize(path), body = bytes, contentType = "application/json")
        ensureSuccess(res, "Сохранение копии")
    }

    suspend fun download(path: String): ByteArray {
        val res = perform("GET", normalize(path))
        ensureSuccess(res, "Загрузка копии")
        return res.body
    }

    suspend fun delete(path: String) {
        val res = perform("DELETE", normalize(path))
        if (res.code in 200..299 || res.code == 404) return
        throw CloudException(messageFor(res.code, "Удаление старой копии"), res.code)
    }

    // ------------------------------------------------------------------
    // Транспорт
    // ------------------------------------------------------------------

    private suspend fun perform(
        method: String,
        path: String,
        body: ByteArray? = null,
        contentType: String = XML,
        depth: Int? = null,
    ): HttpResult {
        var target = url(path)
        var attempts = 0
        while (true) {
            val builder = Request.Builder()
                .url(target)
                .header("Authorization", authHeader)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
            if (depth != null) builder.header("Depth", depth.toString())
            val requestBody = when {
                body != null -> body.toRequestBody(contentType.toMediaType())
                method == "MKCOL" -> ByteArray(0).toRequestBody(null)
                else -> null
            }
            builder.method(method, requestBody)

            val result = call(builder.build())
            val location = result.location
            if (result.code in 300..399 && !location.isNullOrBlank() && attempts < 3) {
                attempts++
                target = if (location.startsWith("http", ignoreCase = true)) {
                    location
                } else {
                    HOST + if (location.startsWith("/")) location else "/$location"
                }
                continue
            }
            return result
        }
    }

    private suspend fun call(request: Request): HttpResult = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            HttpResult(
                code = response.code,
                body = response.body?.bytes() ?: ByteArray(0),
                location = response.header("Location"),
            )
        }
    }

    private fun ensureSuccess(result: HttpResult, what: String) {
        if (result.code in 200..299) return
        throw CloudException(messageFor(result.code, what), result.code)
    }

    private fun messageFor(code: Int, what: String): String = when (code) {
        401 -> "«$what»: Яндекс не принял логин или пароль приложения"
        403 -> "«$what»: доступ запрещён — проверьте, что пароль создан с типом «WebDAV-клиент для Яндекс Диска»"
        404 -> "«$what»: файл или папка не найдены на Яндекс Диске"
        409 -> "«$what»: конфликт имён на Яндекс Диске"
        423 -> "«$what»: файл занят другим приложением"
        429 -> "«$what»: слишком много запросов, попробуйте позже"
        507 -> "«$what»: на Яндекс Диске закончилось свободное место"
        in 500..599 -> "«$what»: сервис Яндекс Диска недоступен (код $code)"
        else -> "«$what»: ошибка Яндекс Диска (код $code)"
    }

    private fun url(path: String): String = HOST + WebDavXml.encodePath(normalize(path))

    private fun normalize(path: String): String {
        val trimmed = path.trim().replace('\\', '/')
        val withLeading = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return withLeading.replace(Regex("/{2,}"), "/")
    }
}
