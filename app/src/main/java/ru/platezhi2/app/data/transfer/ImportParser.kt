package ru.platezhi2.app.data.transfer

import ru.platezhi2.app.core.DueDates
import ru.platezhi2.app.core.Format
import ru.platezhi2.app.data.model.AppIcons
import ru.platezhi2.app.data.model.Card
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.data.model.Recurrence
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Разбор CSV-файлов при импорте.
 *
 * Класс намеренно не зависит от Android, поэтому покрыт обычными unit-тестами.
 *
 * Основная сложность — файл старого приложения «Платежи»: в нём **одна строка
 * соответствует одному периоду оплаты**, а не карточке. Поэтому строки с
 * одинаковым названием объединяются в одну карточку, а строки со статусом
 * «Оплачен» становятся историей оплат.
 *
 * Такой же подход применяется и к другому известному формату — «списку
 * платежей и оплат» с колонками `ID платежа` / `ID записи` (Статус, Тип,
 * Название, Сумма, Дата, Следующая дата, День платежа…). Там одна строка —
 * это одна запись об оплате, а карточка определяется названием.
 */
object ImportParser {

    /** Карточка вместе с историей оплат, прочитанная из файла. */
    data class ParsedCard(val card: Card, val payments: List<Payment>)

    private const val BOM = "\uFEFF"

    private val DATE_FORMATS = listOf(
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ISO_LOCAL_DATE,
    )

    private val KNOWN_HEADERS = setOf(
        "название", "title", "имя",
        "срок оплаты", "дата", "число месяца", "месяц",
        "сумма", "плановая сумма", "amount",
        "повтор", "тип", "значок", "логотип", "заметка", "note", "статус",
        "id платежа", "id записи", "день платежа",
    )

    /** Колонки формата «список платежей и оплат»: одна строка — одна оплата. */
    private val RECORD_HEADERS = setOf(
        "id платежа", "id записи", "id оплаты", "день платежа",
    )

    /** Позиционная схема файла старого приложения (если строки заголовков нет). */
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

    /** Одна строка файла до объединения в карточку. */
    private data class Row(
        val title: String,
        val amountMinor: Long,
        val dueDate: LocalDate?,
        val dueDay: Int?,
        val dueMonth: Int?,
        val recurrence: Recurrence,
        val icon: String,
        val note: String,
        val reminderEnabled: Boolean,
        val reminderDays: Int,
        val time: Pair<Int, Int>?,
        val paid: Boolean,
        val paidAmountMinor: Long,
        val paidDate: LocalDate?,
        val nextDate: LocalDate?,
        val history: List<Pair<LocalDate, Long>>,
    )

    // ------------------------------------------------------------------
    // Точка входа
    // ------------------------------------------------------------------

    fun parseCsv(raw: String): List<ParsedCard> {
        val text = raw.removePrefix(BOM)
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
            // Без строки заголовков принимаем позиционную схему старого приложения,
            // но только если в строке действительно достаточно колонок.
            if (splitLine(lines.first(), delimiter).count { it.isNotBlank() } < 6) return emptyList()
            OLD_POSITIONAL_SCHEMA
        }

        val rows = parseRows(body, delimiter, index)
        // Один из «построчных» форматов: одна строка — один период или одна оплата,
        // а не карточка. Строки с одинаковым названием объединяются в карточку.
        val recordFormat = hasHeader && headerCells.any { it in RECORD_HEADERS }
        val legacy = if (hasHeader) {
            headerCells.any { it.startsWith("срок оплаты") } || recordFormat
        } else {
            true
        }
        return if (legacy) {
            groupLegacy(rows, deriveRecurrence = recordFormat)
        } else {
            rows.map { rowToCard(it) }
        }
    }

    // ------------------------------------------------------------------
    // Разбор строк
    // ------------------------------------------------------------------

    private fun parseRows(body: List<String>, delimiter: Char, index: Map<String, Int>): List<Row> {
        val result = ArrayList<Row>(body.size)
        for (line in body) {
            val row = splitLine(line, delimiter)

            fun cell(vararg names: String): String {
                for (name in names) {
                    val i = index[name]
                    if (i != null && i in row.indices) {
                        val value = row[i].trim()
                        if (value.isNotEmpty()) return value
                    }
                }
                // Заголовки старого приложения могут отличаться («Напомнить за, дней»).
                for (name in names) {
                    val i = index.entries.firstOrNull { it.key.startsWith(name) }?.value
                    if (i != null && i in row.indices) {
                        val value = row[i].trim()
                        if (value.isNotEmpty()) return value
                    }
                }
                return ""
            }

            val dueDate = parseDateOrNull(cell("срок оплаты", "дата"))
            val dueDayCell = cell("число месяца", "число", "день платежа", "день")
                .filter { it.isDigit() }.toIntOrNull()
            val dueMonthCell = cell("месяц").filter { it.isDigit() }.toIntOrNull()
            val time = parseTime(cell("время", "время напоминания"))
            val reminderRaw = cell("напомнить за (дней)", "напомнить за", "напомнить")
            val reminderDaysValue = reminderRaw.filter { it.isDigit() }.toIntOrNull()
            val reminderFlag = cell("напоминание")
            val reminderEnabled = when {
                reminderFlag.equals("да", true) -> true
                reminderFlag.equals("нет", true) -> false
                reminderDaysValue != null -> true
                else -> false
            }

            val history = ArrayList<Pair<LocalDate, Long>>()
            cell("история оплат", "оплаты").split('|').forEach { entry ->
                val trimmed = entry.trim()
                if (trimmed.isEmpty()) return@forEach
                val parts = trimmed.split('=')
                val paidAt = parseDateOrNull(parts[0])
                if (paidAt != null) {
                    history += paidAt to (if (parts.size > 1) amountToMinor(parts[1]) else 0L)
                } else {
                    val alt = trimmed.split(':')
                    val altDate = parseDateOrNull(alt.getOrNull(0).orEmpty())
                    if (altDate != null) {
                        history += altDate to (if (alt.size > 1) amountToMinor(alt[1]) else 0L)
                    }
                }
            }

            result += Row(
                title = cell("название", "title", "имя").trim(),
                amountMinor = amountToMinor(cell("сумма", "плановая сумма", "amount")),
                dueDate = dueDate,
                dueDay = dueDate?.dayOfMonth ?: dueDayCell,
                dueMonth = dueDate?.monthValue ?: dueMonthCell,
                recurrence = recurrenceFrom(cell("повтор", "периодичность", "recurrence", "тип")),
                icon = cell("значок", "иконка", "icon", "логотип"),
                note = cell("заметка", "note", "комментарий"),
                reminderEnabled = reminderEnabled,
                reminderDays = reminderDaysValue ?: 1,
                time = time,
                paid = isPaidText(cell("статус")),
                paidAmountMinor = amountToMinor(cell("оплачено")),
                paidDate = parseDateOrNull(cell("фактическая дата", "дата")),
                nextDate = parseDateOrNull(cell("следующая дата")),
                history = history,
            )
        }
        return result
    }

    /** Строка собственного формата: одна строка — одна карточка. */
    private fun rowToCard(row: Row): ParsedCard {
        val card = Card(
            title = row.title.trim(),
            amountMinor = row.amountMinor,
            dueDay = (row.dueDay ?: 1).coerceIn(1, 31),
            dueMonth = (row.dueMonth ?: 1).coerceIn(1, 12),
            recurrence = row.recurrence,
            icon = normalizeIcon(row.icon),
            note = row.note,
            reminderEnabled = row.reminderEnabled,
            reminderDaysBefore = row.reminderDays.coerceIn(0, 60),
            reminderHour = row.time?.first ?: 9,
            reminderMinute = row.time?.second ?: 0,
        )

        val payments = when {
            row.history.isNotEmpty() -> row.history.map { (paidAt, amount) ->
                Payment(
                    periodEpochDay = DueDates.periodForPaidDate(card, paidAt).toEpochDay(),
                    paidAtEpochDay = paidAt.toEpochDay(),
                    amountMinor = amount.takeIf { it > 0L } ?: card.amountMinor,
                )
            }
            row.paid -> {
                val raw = row.dueDate ?: row.paidDate ?: LocalDate.now()
                val period = if (card.recurrence == Recurrence.NONE) raw
                else DueDates.currentPeriodDue(card.dueDay, card.dueMonth, card.recurrence, raw)
                listOf(
                    Payment(
                        periodEpochDay = period.toEpochDay(),
                        paidAtEpochDay = (row.paidDate ?: period).toEpochDay(),
                        amountMinor = row.paidAmountMinor.takeIf { it > 0L } ?: card.amountMinor,
                    )
                )
            }
            else -> emptyList()
        }
        return ParsedCard(card, dedupePayments(payments))
    }

    /** Строки «построчных» файлов объединяются в карточки по названию. */
    private fun groupLegacy(rows: List<Row>, deriveRecurrence: Boolean = false): List<ParsedCard> {
        val groups = LinkedHashMap<String, MutableList<Row>>()
        rows.forEach { row ->
            val key = normalizeTitle(row.title)
            if (key.isEmpty()) return@forEach
            groups.getOrPut(key) { mutableListOf() }.add(row)
        }
        return groups.values.map { buildLegacyCard(it, deriveRecurrence) }
    }

    private fun buildLegacyCard(list: List<Row>, deriveRecurrence: Boolean = false): ParsedCard {
        val sorted = list.sortedBy { it.dueDate ?: LocalDate.MIN }
        val planned = sorted.filter { !it.paid }
        val latest = sorted.last()

        // Срок: берём из ближайшей неоплаченной строки, иначе — самый частый день месяца.
        val modeDay = sorted.mapNotNull { it.dueDate?.dayOfMonth }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val dueSource = planned.lastOrNull()?.dueDate
        val dueDay = (dueSource?.dayOfMonth ?: modeDay ?: 1).coerceIn(1, 31)
        val dueMonth = (dueSource?.monthValue ?: latest.dueDate?.monthValue ?: 1).coerceIn(1, 12)

        var recurrence = sorted.map { it.recurrence }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: latest.recurrence
        // В «построчных» файлах тип записи («Разовый») не описывает саму карточку.
        // Если оплаты растянуты на разные месяцы — это повторяющийся платёж.
        if (deriveRecurrence && recurrence == Recurrence.NONE) {
            val months = sorted.mapNotNull { it.dueDate?.let { d -> d.year * 12 + d.monthValue } }
                .toSet()
            if (months.size >= 2) recurrence = Recurrence.MONTHLY
        }

        val amount = latest.amountMinor.takeIf { it > 0L }
            ?: sorted.lastOrNull { it.amountMinor > 0L }?.amountMinor
            ?: 0L

        val icon = sorted.lastOrNull { it.icon.isNotBlank() }?.icon ?: ""
        val note = sorted.lastOrNull { it.note.isNotBlank() }?.note ?: ""
        val reminder = sorted.lastOrNull { it.reminderEnabled }
        val time = sorted.lastOrNull { it.time != null }?.time

        val byPeriod = LinkedHashMap<Long, Payment>()
        sorted.forEach { row ->
            if (!row.paid) return@forEach
            val paidAt = row.paidDate ?: row.dueDate ?: return@forEach
            // Дату периода приводим к числу месяца карточки: в файлах старого
            // приложения оплаты записаны фактическим днём оплаты, который может
            // не совпадать с числом месяца в карточке. Без этого прошлые месяцы
            // не находили свою оплату и выглядели просроченными.
            val period = if (recurrence == Recurrence.NONE) {
                row.dueDate ?: paidAt
            } else {
                DueDates.currentPeriodDue(dueDay, dueMonth, recurrence, row.dueDate ?: paidAt)
            }
            val amountMinor = row.paidAmountMinor.takeIf { it > 0L }
                ?: row.amountMinor.takeIf { it > 0L }
                ?: amount
            byPeriod[period.toEpochDay()] = Payment(
                periodEpochDay = period.toEpochDay(),
                paidAtEpochDay = paidAt.toEpochDay(),
                amountMinor = amountMinor,
            )
        }

        val card = Card(
            title = latest.title.trim(),
            amountMinor = amount,
            dueDay = dueDay,
            dueMonth = dueMonth,
            recurrence = recurrence,
            icon = normalizeIcon(icon),
            note = note,
            reminderEnabled = reminder != null,
            reminderDaysBefore = (reminder?.reminderDays ?: 3).coerceIn(0, 60),
            reminderHour = time?.first ?: 9,
            reminderMinute = time?.second ?: 0,
        )
        return ParsedCard(card, byPeriod.values.toList())
    }

    fun dedupePayments(payments: List<Payment>): List<Payment> {
        val byPeriod = LinkedHashMap<Long, Payment>()
        payments.forEach { payment -> byPeriod[payment.periodEpochDay] = payment }
        return byPeriod.values.toList()
    }

    // ------------------------------------------------------------------
    // Вспомогательные преобразования
    // ------------------------------------------------------------------

    private fun detectDelimiter(headerLine: String): Char {
        val semicolons = headerLine.count { it == ';' }
        val commas = headerLine.count { it == ',' }
        return if (semicolons >= commas) ';' else ','
    }

    private fun normalizeHeader(raw: String): String =
        raw.trim().trim('"').lowercase().replace('\u00A0', ' ')

    private fun normalizeTitle(title: String): String =
        title.trim().lowercase().replace(Regex("\\s+"), " ")

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

    fun parseDateOrNull(text: String): LocalDate? {
        val value = text.trim().trim('"')
        if (value.isEmpty()) return null
        for (format in DATE_FORMATS) {
            runCatching { return LocalDate.parse(value, format) }
        }
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

    fun isPaidText(text: String): Boolean {
        val v = text.trim().lowercase()
        if (v.isEmpty()) return false
        return (v.startsWith("оплач") && !v.startsWith("не ")) ||
            v == "да" || v == "true" || v == "yes" || v == "1"
    }

    fun recurrenceFrom(text: String): Recurrence {
        val v = text.trim().lowercase()
        return when {
            v.contains("год") || v.contains("year") -> Recurrence.YEARLY
            v.contains("месяц") || v.contains("month") -> Recurrence.MONTHLY
            v.contains("нед") || v.contains("week") -> Recurrence.MONTHLY
            v.contains("разов") || v.contains("не повтор") || v == "none" -> Recurrence.NONE
            else -> Recurrence.MONTHLY
        }
    }

    /** Значок: эмодзи берём как есть, названия значков старого приложения переводим. */
    fun normalizeIcon(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return AppIcons.DEFAULT
        if (value.any { it.code > 127 }) return value
        val v = value.lowercase()
        return when {
            v.contains("smartphone") || v.contains("phone") || v.contains("mobile") || v.contains("sim") -> "📱"
            v.contains("landmark") || v.contains("bank") || v.contains("credit") || v.contains("wallet") || v.contains("card") -> "🏦"
            v.contains("house") || v.contains("home") -> "🏠"
            v.contains("router") || v.contains("wifi") || v.contains("internet") || v.contains("network") -> "📶"
            v.contains("plug") || v.contains("zap") || v.contains("electr") || v.contains("light") || v.contains("bulb") -> "💡"
            v.contains("triangle") || v.contains("alert") || v.contains("warn") -> "⚠️"
            v.contains("shield") -> "🛡"
            v.contains("receipt") || v.contains("document") || v.contains("bill") -> "🧾"
            v.contains("car") || v.contains("auto") -> "🚗"
            v.contains("water") || v.contains("droplet") -> "💧"
            v.contains("flame") || v.contains("fire") || v.contains("gas") -> "🔥"
            v.contains("tv") || v.contains("television") -> "📺"
            v.contains("music") -> "🎵"
            v.contains("health") || v.contains("medic") || v.contains("pill") || v.contains("hospital") -> "💊"
            v.contains("cart") || v.contains("shop") || v.contains("store") -> "🛒"
            v.contains("graduat") || v.contains("school") || v.contains("educat") -> "🎓"
            v.contains("gift") -> "🎁"
            v.contains("plane") || v.contains("flight") -> "✈️"
            v.contains("bus") -> "🚌"
            v.contains("dog") || v.contains("pet") -> "🐶"
            v.contains("bell") || v.contains("remind") -> "🔔"
            else -> AppIcons.DEFAULT
        }
    }
}
