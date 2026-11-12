package ru.platezhi2.app.data.transfer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.platezhi2.app.core.DueDates
import ru.platezhi2.app.core.Format
import ru.platezhi2.app.data.model.AppIcons
import ru.platezhi2.app.data.model.Card
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.data.model.Recurrence
import ru.platezhi2.app.data.repo.PaymentRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Файл резервной копии в облачной папке. */
data class BackupFile(val name: String, val uri: Uri, val modified: Long)

/**
 * Экспорт и импорт данных.
 *
 * Данные состоят из двух частей: карточки платежей и история оплат по ним.
 * Поддерживается импорт:
 *  - JSON собственного формата (карточки + вложенная история оплат);
 *  - JSON предыдущей версии (плоский список карточек);
 *  - CSV собственного формата (разделитель «;», история — в отдельной колонке);
 *  - CSV старого приложения «Платежи» (разделитель «,», заголовки на русском,
 *    дата dd.MM.yyyy, сумма «руб.коп»).
 */
class TransferManager(
    private val context: Context,
    private val repo: PaymentRepository,
) {

    companion object {
        private const val BOM = "\uFEFF"

        private val HEADER = listOf(
            "Название", "Сумма", "Число месяца", "Месяц", "Повтор", "Значок",
            "Напоминание", "Напомнить за (дней)", "Время", "Заметка", "История оплат",
        )

        private val DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE,
        )

        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

        private val KNOWN_HEADERS = setOf(
            "название", "title", "имя",
            "срок оплаты", "дата", "число месяца", "месяц",
            "сумма", "плановая сумма", "amount",
            "повтор", "значок", "заметка", "note", "статус",
        )

        /** Позиционная схема файла старого приложения. */
        private val OLD_POSITIONAL_SCHEMA = mapOf(
            "название" to 0,
            "срок оплаты" to 1,
            "плановая сумма" to 2,
            "тип суммы" to 3,
            "оплачено" to 4,
            "фактическая дата" to 5,
            "статус" to 6,
            "повтор" to 7,
            "следующая дата" to 8,
            "значок" to 9,
            "напомнить за (дней)" to 10,
            "заметка" to 11,
        )
    }

    /** Карточка с историей, прочитанная из файла. */
    private data class ImportedCard(val card: Card, val payments: List<Payment>)

    // ------------------------------------------------------------------
    // Экспорт
    // ------------------------------------------------------------------

    suspend fun exportJson(uri: Uri) = writeText(uri, buildJsonText())

    suspend fun exportCsv(uri: Uri) = writeText(uri, buildCsvText())

    /** Экспорт в выбранную облачную папку. Возвращает созданный файл. */
    suspend fun exportToFolder(treeUri: Uri, json: Boolean): Uri? = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
        val stamp = LocalDate.now().toString()
        val name = "platezhi2-$stamp.${if (json) "json" else "csv"}"
        val mime = if (json) "application/json" else "text/csv"
        val file = dir.createFile(mime, name) ?: return@withContext null
        val text = if (json) buildJsonText() else buildCsvText()
        context.contentResolver.openOutputStream(file.uri, "wt")
            ?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        file.uri
    }

    /** Резервные копии в выбранной облачной папке, свежие — первыми. */
    suspend fun listBackups(treeUri: Uri): List<BackupFile> = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        dir.listFiles()
            .filter { file ->
                val name = file.name.orEmpty()
                file.isFile && (name.endsWith(".json", true) || name.endsWith(".csv", true))
            }
            .sortedByDescending { it.lastModified() }
            .map { BackupFile(it.name.orEmpty(), it.uri, it.lastModified()) }
    }

    private suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("Не удалось открыть файл для записи")
    }

    private suspend fun buildJsonText(): String {
        val cards = repo.getCards()
        val byCard = repo.getPayments().groupBy { it.cardId }
        val root = JSONObject()
        root.put("version", 2)
        root.put(
            "cards",
            JSONArray().apply {
                cards.forEach { card -> put(cardJson(card, byCard[card.id].orEmpty())) }
            },
        )
        return root.toString(2)
    }

    private fun cardJson(card: Card, history: List<Payment>): JSONObject = JSONObject().apply {
        put("title", card.title)
        put("amountMinor", card.amountMinor)
        put("dueDay", card.dueDay)
        put("dueMonth", card.dueMonth)
        put("recurrence", card.recurrence.key)
        put("icon", card.icon)
        put("note", card.note)
        put("reminderEnabled", card.reminderEnabled)
        put("reminderDaysBefore", card.reminderDaysBefore)
        put("reminderHour", card.reminderHour)
        put("reminderMinute", card.reminderMinute)
        put(
            "payments",
            JSONArray().apply {
                history.sortedBy { it.paidAtEpochDay }.forEach { payment ->
                    put(
                        JSONObject().apply {
                            put("period", LocalDate.ofEpochDay(payment.periodEpochDay).toString())
                            put("paidAt", LocalDate.ofEpochDay(payment.paidAtEpochDay).toString())
                            put("amountMinor", payment.amountMinor)
                        }
                    )
                }
            },
        )
    }

    private suspend fun buildCsvText(): String {
        val cards = repo.getCards()
        val byCard = repo.getPayments().groupBy { it.cardId }
        val sb = StringBuilder()
        sb.append(BOM)
        sb.append(HEADER.joinToString(";")).append('\n')
        cards.forEach { card ->
            val history = byCard[card.id].orEmpty().sortedBy { it.paidAtEpochDay }
            val row = listOf(
                card.title,
                amountToText(card.amountMinor),
                card.dueDay.toString(),
                card.dueMonth.toString(),
                recurrenceToText(card.recurrence),
                card.icon,
                if (card.reminderEnabled) "да" else "нет",
                card.reminderDaysBefore.toString(),
                "%02d:%02d".format(card.reminderHour, card.reminderMinute),
                card.note,
                history.joinToString("|") { payment ->
                    val date = LocalDate.ofEpochDay(payment.paidAtEpochDay).format(DATE_FORMATS[0])
                    "$date=${amountToText(payment.amountMinor)}"
                },
            )
            sb.append(row.joinToString(";") { escape(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun amountToText(minor: Long): String {
        val rub = minor / 100
        val kop = minor % 100
        return if (kop == 0L) rub.toString() else "$rub.${kop.toString().padStart(2, '0')}"
    }

    // ------------------------------------------------------------------
    // Импорт
    // ------------------------------------------------------------------

    /**
     * Импортирует данные, распознавая формат автоматически.
     * Возвращает число импортированных карточек.
     */
    suspend fun importFrom(uri: Uri, replaceAll: Boolean): Int = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: error("Не удалось прочитать файл")

        val text = raw.removePrefix(BOM).trim()
        if (text.isEmpty()) error("Файл пуст")

        val parsed = if (text.startsWith("[") || text.startsWith("{")) parseJson(text) else parseCsv(text)
        val meaningful = parsed.filter {
            it.card.title.isNotBlank() || it.card.amountMinor != 0L || it.payments.isNotEmpty()
        }
        if (meaningful.isEmpty()) error("Не удалось распознать формат файла")

        if (replaceAll) repo.clearAll()

        val newIds = repo.insertCards(meaningful.map { it.card })
        val payments = ArrayList<Payment>()
        meaningful.forEachIndexed { index, imported ->
            val cardId = newIds.getOrNull(index) ?: return@forEachIndexed
            imported.payments.forEach { payment -> payments += payment.copy(cardId = cardId) }
        }
        repo.insertPayments(payments)
        meaningful.size
    }

    // ---------- JSON ----------

    private fun parseJson(text: String): List<ImportedCard> {
        val array = if (text.startsWith("{")) {
            val root = JSONObject(text)
            root.optJSONArray("cards") ?: JSONArray().put(root)
        } else {
            JSONArray(text)
        }

        val result = ArrayList<ImportedCard>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val dueDay = o.optInt("dueDay", 0)
            val dueMonth = o.optInt("dueMonth", 0)
            val card = Card(
                title = o.optString("title").takeIf { it.isNotBlank() }
                    ?: o.optString("name").takeIf { it.isNotBlank() }
                    ?: "",
                amountMinor = if (o.has("amountMinor")) o.optLong("amountMinor") else amountToMinor(o.optString("amount")),
                dueDay = dueDay.coerceIn(1, 31),
                dueMonth = if (dueMonth in 1..12) dueMonth else 1,
                recurrence = recurrenceFrom(o.optString("recurrence")),
                icon = normalizeIcon(o.optString("icon")),
                note = o.optString("note"),
                reminderEnabled = o.optBoolean("reminderEnabled", false),
                reminderDaysBefore = o.optInt("reminderDaysBefore", 1).coerceIn(0, 60),
                reminderHour = o.optInt("reminderHour", 9).coerceIn(0, 23),
                reminderMinute = o.optInt("reminderMinute", 0).coerceIn(0, 59),
            )

            val payments = ArrayList<Payment>()
            val arrayPayments = o.optJSONArray("payments")
            if (arrayPayments != null) {
                for (j in 0 until arrayPayments.length()) {
                    val p = arrayPayments.optJSONObject(j) ?: continue
                    val period = parseDateOrNull(p.optString("period"))
                    val paidAt = parseDateOrNull(p.optString("paidAt"))
                        ?: parseDateOrNull(p.optString("paidDate"))
                        ?: period
                        ?: continue
                    payments += Payment(
                        periodEpochDay = (period ?: paidAt).toEpochDay(),
                        paidAtEpochDay = paidAt.toEpochDay(),
                        amountMinor = if (p.has("amountMinor")) p.optLong("amountMinor") else card.amountMinor,
                    )
                }
            } else {
                // Формат предыдущей версии: единый флаг «оплачено до».
                val paidUntil = o.optLong("paidUntil", 0L)
                if (paidUntil > 0L) {
                    payments += Payment(
                        periodEpochDay = paidUntil,
                        paidAtEpochDay = paidUntil,
                        amountMinor = card.amountMinor,
                    )
                } else if (o.optBoolean("paid", false)) {
                    val paidAt = LocalDate.now().toEpochDay()
                    payments += Payment(
                        periodEpochDay = DueDates.periodForPaidDate(card, LocalDate.ofEpochDay(paidAt)).toEpochDay(),
                        paidAtEpochDay = paidAt,
                        amountMinor = card.amountMinor,
                    )
                }
            }

            result += ImportedCard(card, payments)
        }
        return result
    }

    // ---------- CSV ----------

    private fun parseCsv(text: String): List<ImportedCard> {
        val lines = text.split('\n').map { it.trim('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val delimiter = detectDelimiter(lines.first())
        val headerCells = splitLine(lines.first(), delimiter).map { normalizeHeader(it) }
        val hasHeader = headerCells.any { it in KNOWN_HEADERS } ||
            (headerCells.count { it.isNotBlank() } >= 3 &&
                headerCells.any { it.contains("назван") || it.contains("сумм") })

        val body = if (hasHeader) lines.drop(1) else lines
        val index: Map<String, Int> = if (hasHeader) {
            headerCells.withIndex().associate { (i, name) -> name to i }
        } else {
            OLD_POSITIONAL_SCHEMA
        }

        fun cell(row: List<String>, vararg names: String): String {
            for (name in names) {
                val i = index[name.lowercase()] ?: continue
                if (i in row.indices) {
                    val value = row[i].trim()
                    if (value.isNotEmpty()) return value
                }
            }
            return ""
        }

        val result = ArrayList<ImportedCard>()
        for (line in body) {
            val row = splitLine(line, delimiter)
            val dueText = cell(row, "срок оплаты", "дата")
            val dueDate = parseDateOrNull(dueText)

            val time = parseTime(cell(row, "время", "время напоминания"))
            val reminderRaw = cell(row, "напомнить за (дней)", "напомнить за", "напомнить")
            val reminderDaysValue = reminderRaw.filter { it.isDigit() }.toIntOrNull()
            val reminderFlag = cell(row, "напоминание")
            val reminderEnabled = when {
                reminderFlag.equals("да", true) -> true
                reminderFlag.equals("нет", true) -> false
                reminderDaysValue != null && reminderDaysValue > 0 -> true
                else -> false
            }

            val card = Card(
                title = cell(row, "название", "title", "имя").trim(),
                amountMinor = amountToMinor(cell(row, "сумма", "плановая сумма", "amount")),
                dueDay = (dueDate?.dayOfMonth
                    ?: cell(row, "число месяца", "число", "день").filter { it.isDigit() }.toIntOrNull()
                    ?: 1).coerceIn(1, 31),
                dueMonth = (dueDate?.monthValue
                    ?: cell(row, "месяц").filter { it.isDigit() }.toIntOrNull()
                    ?: 1).coerceIn(1, 12),
                recurrence = recurrenceFrom(cell(row, "повтор", "периодичность", "recurrence")),
                icon = normalizeIcon(cell(row, "значок", "иконка", "icon")),
                note = cell(row, "заметка", "note", "комментарий"),
                reminderEnabled = reminderEnabled,
                reminderDaysBefore = (reminderDaysValue ?: 1).coerceIn(0, 60),
                reminderHour = time?.first ?: 9,
                reminderMinute = time?.second ?: 0,
            )

            val payments = ArrayList<Payment>()

            // История оплат в собственной колонке.
            val historyText = cell(row, "история оплат", "оплаты")
            if (historyText.isNotEmpty()) {
                historyText.split('|', ',').forEach { entry ->
                    val trimmed = entry.trim()
                    if (trimmed.isEmpty()) return@forEach
                    val parts = trimmed.split('=')
                    val paidAt = parseDateOrNull(parts[0])
                    if (paidAt == null) {
                        // Возможно, формат «дата:сумма».
                        val alt = trimmed.split(':')
                        val altDate = parseDateOrNull(alt.getOrNull(0).orEmpty())
                        if (altDate != null) {
                            payments += Payment(
                                periodEpochDay = DueDates.periodForPaidDate(card, altDate).toEpochDay(),
                                paidAtEpochDay = altDate.toEpochDay(),
                                amountMinor = if (alt.size > 1) amountToMinor(alt[1]) else card.amountMinor,
                            )
                        }
                        return@forEach
                    }
                    payments += Payment(
                        periodEpochDay = DueDates.periodForPaidDate(card, paidAt).toEpochDay(),
                        paidAtEpochDay = paidAt.toEpochDay(),
                        amountMinor = if (parts.size > 1) amountToMinor(parts[1]) else card.amountMinor,
                    )
                }
            }

            // Формат старого приложения: одна оплата на строку.
            if (payments.isEmpty()) {
                val statusText = cell(row, "статус")
                if (isPaidText(statusText)) {
                    val actualAmount = amountToMinor(cell(row, "оплачено"))
                    val actualDate = parseDateOrNull(cell(row, "фактическая дата"))
                        ?: dueDate
                        ?: LocalDate.now()
                    payments += Payment(
                        periodEpochDay = (dueDate ?: actualDate).toEpochDay(),
                        paidAtEpochDay = actualDate.toEpochDay(),
                        amountMinor = if (actualAmount > 0L) actualAmount else card.amountMinor,
                    )
                }
            }

            result += ImportedCard(card, payments)
        }
        return result
    }

    private fun detectDelimiter(headerLine: String): Char {
        val semicolons = headerLine.count { it == ';' }
        val commas = headerLine.count { it == ',' }
        return if (semicolons >= commas) ';' else ','
    }

    private fun normalizeHeader(raw: String): String =
        raw.trim().trim('"').lowercase().replace('\u00A0', ' ')

    private fun splitLine(line: String, delimiter: Char): List<String> {
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
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    result.add(sb.toString()); sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    private fun escape(value: String): String =
        if (value.contains(';') || value.contains('\n') || value.contains('"')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value

    // ---------- Общие преобразования ----------

    private fun parseDateOrNull(text: String): LocalDate? {
        val value = text.trim().trim('"')
        if (value.isEmpty()) return null
        for (format in DATE_FORMATS) {
            runCatching { return LocalDate.parse(value, format) }
        }
        // Дополнительно: метка времени.
        runCatching { return LocalDate.parse(value.take(10)) }
        return null
    }

    private fun parseTime(text: String): Pair<Int, Int>? {
        val value = text.trim()
        if (value.isEmpty()) return null
        val parts = value.split(':')
        if (parts.size < 2) return null
        val hour = parts[0].filter { it.isDigit() }.toIntOrNull() ?: return null
        val minute = parts[1].filter { it.isDigit() }.toIntOrNull() ?: return null
        return hour.coerceIn(0, 23) to minute.coerceIn(0, 59)
    }

    private fun amountToMinor(text: String): Long = Format.parseAmountToMinor(text) ?: 0L

    private fun isPaidText(text: String): Boolean {
        val v = text.trim().lowercase()
        if (v.isEmpty()) return false
        return (v.startsWith("оплач") && !v.startsWith("не ")) ||
            v == "да" || v == "true" || v == "yes" || v == "1"
    }

    private fun recurrenceFrom(text: String): Recurrence {
        val v = text.trim().lowercase()
        return when {
            v.contains("год") || v.contains("year") -> Recurrence.YEARLY
            v.contains("месяц") || v.contains("month") -> Recurrence.MONTHLY
            v.contains("нед") || v.contains("week") -> Recurrence.MONTHLY
            v.contains("разов") || v.contains("не повтор") || v == "none" -> Recurrence.NONE
            v.isEmpty() -> Recurrence.MONTHLY
            else -> Recurrence.MONTHLY
        }
    }

    private fun recurrenceToText(recurrence: Recurrence): String = when (recurrence) {
        Recurrence.NONE -> "Не повторять"
        Recurrence.MONTHLY -> "Ежемесячно"
        Recurrence.YEARLY -> "Ежегодно"
    }

    /** Значок: эмодзи берём как есть, текстовые ключи переводим в эмодзи. */
    private fun normalizeIcon(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return AppIcons.DEFAULT
        if (value.any { it.code > 127 }) return value
        val lower = value.lowercase()
        return when {
            lower.contains("wallet") || lower.contains("card") || lower.contains("credit") -> "💳"
            lower.contains("home") || lower.contains("house") || lower.contains("flat") -> "🏠"
            lower.contains("car") || lower.contains("auto") -> "🚗"
            lower.contains("phone") || lower.contains("mobile") || lower.contains("sim") -> "📱"
            lower.contains("internet") || lower.contains("wifi") -> "📶"
            lower.contains("light") || lower.contains("bulb") || lower.contains("electr") || lower.contains("energy") -> "💡"
            lower.contains("water") -> "💧"
            lower.contains("gas") || lower.contains("fire") -> "🔥"
            lower.contains("tv") || lower.contains("television") -> "📺"
            lower.contains("music") -> "🎵"
            lower.contains("health") || lower.contains("medic") -> "💊"
            lower.contains("educat") || lower.contains("school") -> "🎓"
            lower.contains("gift") -> "🎁"
            lower.contains("pet") || lower.contains("dog") -> "🐶"
            lower.contains("bell") || lower.contains("remind") -> "🔔"
            else -> AppIcons.DEFAULT
        }
    }
}
