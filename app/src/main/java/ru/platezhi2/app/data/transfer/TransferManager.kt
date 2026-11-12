package ru.platezhi2.app.data.transfer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.data.model.Recurrence
import ru.platezhi2.app.data.repo.PaymentRepository

/** Экспорт и импорт платежей в файл (SAF) и в выбранную облачную папку. */
class TransferManager(
    private val context: Context,
    private val repo: PaymentRepository,
) {

    // ---------- Экспорт ----------

    suspend fun exportJson(uri: Uri) = withContext(Dispatchers.IO) {
        val json = buildJson(repo.getAll()).toString(2)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("Не удалось открыть файл для записи")
    }

    suspend fun exportCsv(uri: Uri) = withContext(Dispatchers.IO) {
        val csv = buildCsv(repo.getAll())
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(csv.toByteArray(Charsets.UTF_8))
        } ?: error("Не удалось открыть файл для записи")
    }

    /** Экспорт в облачную папку (SAF tree). Возвращает созданный файл. */
    suspend fun exportToFolder(treeUri: Uri, json: Boolean): Uri? = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
        val name = "platezhi2-backup-${System.currentTimeMillis()}.${if (json) "json" else "csv"}"
        val mime = if (json) "application/json" else "text/csv"
        val file = dir.createFile(mime, name) ?: return@withContext null
        val data = if (json) buildJson(repo.getAll()).toString(2) else buildCsv(repo.getAll())
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
            out.write(data.toByteArray(Charsets.UTF_8))
        }
        file.uri
    }

    // ---------- Импорт ----------

    /** Импортирует данные, автоматически распознавая JSON или CSV. Возвращает количество записей. */
    suspend fun importFrom(uri: Uri, replaceAll: Boolean): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: error("Не удалось прочитать файл")

        val trimmed = text.trimStart()
        val payments = if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            parseJson(text)
        } else {
            parseCsv(text)
        }

        if (payments.isEmpty()) return@withContext 0
        if (replaceAll) {
            repo.replaceAll(payments)
        } else {
            payments.forEach { repo.save(it.copy(id = 0)) }
        }
        payments.size
    }

    // ---------- JSON ----------

    private fun buildJson(items: List<Payment>): JSONArray {
        val arr = JSONArray()
        items.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("title", p.title)
                    put("amountMinor", p.amountMinor)
                    put("currency", p.currency)
                    put("dueDateEpochDay", p.dueDateEpochDay)
                    put("recurrence", p.recurrence.key)
                    put("category", p.category)
                    put("logoPath", p.logoPath ?: JSONObject.NULL)
                    put("note", p.note)
                    put("paid", p.paid)
                    put("reminderEnabled", p.reminderEnabled)
                    put("reminderDaysBefore", p.reminderDaysBefore)
                    put("reminderHour", p.reminderHour)
                    put("reminderMinute", p.reminderMinute)
                }
            )
        }
        return arr
    }

    private fun parseJson(text: String): List<Payment> {
        val array = if (text.trimStart().startsWith("{")) {
            JSONObject(text).optJSONArray("payments") ?: JSONArray()
        } else {
            JSONArray(text)
        }
        val result = ArrayList<Payment>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            result.add(
                Payment(
                    title = o.optString("title"),
                    amountMinor = o.optLong("amountMinor"),
                    currency = o.optString("currency", "RUB"),
                    dueDateEpochDay = o.optLong("dueDateEpochDay"),
                    recurrence = Recurrence.fromKey(o.optString("recurrence")),
                    category = o.optString("category", "other"),
                    logoPath = o.optString("logoPath").ifBlank { null },
                    note = o.optString("note"),
                    paid = o.optBoolean("paid"),
                    reminderEnabled = o.optBoolean("reminderEnabled"),
                    reminderDaysBefore = o.optInt("reminderDaysBefore", 1),
                    reminderHour = o.optInt("reminderHour", 9),
                    reminderMinute = o.optInt("reminderMinute", 0),
                )
            )
        }
        return result
    }

    // ---------- CSV ----------

    private val header = listOf(
        "title", "amountMinor", "currency", "dueDateEpochDay", "recurrence", "category",
        "note", "paid", "reminderEnabled", "reminderDaysBefore", "reminderHour", "reminderMinute"
    )

    private fun buildCsv(items: List<Payment>): String {
        val sb = StringBuilder()
        sb.append(header.joinToString(";")).append('\n')
        items.forEach { p ->
            val row = listOf(
                p.title, p.amountMinor.toString(), p.currency, p.dueDateEpochDay.toString(),
                p.recurrence.key, p.category, p.note, p.paid.toString(),
                p.reminderEnabled.toString(), p.reminderDaysBefore.toString(),
                p.reminderHour.toString(), p.reminderMinute.toString()
            )
            sb.append(row.joinToString(";") { csvEscape(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun parseCsv(text: String): List<Payment> {
        val lines = text.split('\n').map { it.trim('\r') }.filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val cols = splitCsvLine(lines[0])
        val index = header.associateWith { h -> cols.indexOfFirst { it.equals(h, ignoreCase = true) } }
        fun cell(row: List<String>, name: String): String {
            val idx = index[name] ?: -1
            return if (idx in row.indices) row[idx] else ""
        }
        val result = ArrayList<Payment>()
        for (line in lines.drop(1)) {
            val row = splitCsvLine(line)
            result.add(
                Payment(
                    title = cell(row, "title"),
                    amountMinor = cell(row, "amountMinor").toLongOrNull() ?: 0L,
                    currency = cell(row, "currency").ifBlank { "RUB" },
                    dueDateEpochDay = cell(row, "dueDateEpochDay").toLongOrNull() ?: 0L,
                    recurrence = Recurrence.fromKey(cell(row, "recurrence")),
                    category = cell(row, "category").ifBlank { "other" },
                    note = cell(row, "note"),
                    paid = cell(row, "paid").toBooleanStrictOrNull() ?: false,
                    reminderEnabled = cell(row, "reminderEnabled").toBooleanStrictOrNull() ?: false,
                    reminderDaysBefore = cell(row, "reminderDaysBefore").toIntOrNull() ?: 1,
                    reminderHour = cell(row, "reminderHour").toIntOrNull() ?: 9,
                    reminderMinute = cell(row, "reminderMinute").toIntOrNull() ?: 0,
                )
            )
        }
        return result
    }

    private fun csvEscape(value: String): String =
        if (value.contains(';') || value.contains('\n') || value.contains('"')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value

    private fun splitCsvLine(line: String): List<String> {
        val result = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else inQuotes = !inQuotes
                }
                c == ';' && !inQuotes -> { result.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }
}
