package ru.platezhi2.app.core

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

object Format {

    private val ru = Locale("ru", "RU")
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", ru)
    private val fullDate = DateTimeFormatter.ofPattern("d MMMM yyyy", ru)
    private val integer = NumberFormat.getIntegerInstance(ru)

    /** Формат даты старого приложения — для импорта. */
    val legacyDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Сумма без копеек: «1 235». Копейки нигде не показываем. */
    fun money(amountMinor: Long): String = integer.format(Math.round(amountMinor / 100.0))

    /** Значение для поля ввода суммы: «1235». Копейки не показываем. */
    fun amountInput(amountMinor: Long): String {
        if (amountMinor == 0L) return ""
        return Math.round(amountMinor / 100.0).toString()
    }

    /** Разбор «1 234,56» → копейки. Возвращает null при некорректном вводе. */
    fun parseAmountToMinor(text: String): Long? {
        val cleaned = text.trim()
            .replace("\u00A0", "")
            .replace(" ", "")
            .replace("\u2009", "")
            .replace(",", ".")
        if (cleaned.isEmpty()) return 0L
        val value = cleaned.toDoubleOrNull() ?: return null
        return (value * 100.0).roundToLong()
    }

    fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(fullDate)
    fun date(date: LocalDate): String = date.format(fullDate)
    fun dayMonth(date: LocalDate): String = date.format(dayMonth)

    private val monthTitle = DateTimeFormatter.ofPattern("LLLL yyyy", ru)

    /** «Ноябрь 2026». */
    fun month(month: java.time.YearMonth): String =
        month.format(monthTitle).replaceFirstChar { it.uppercase(ru) }

    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    /** «сегодня», «завтра», «через 3 дня», «просрочен на 2 дня». */
    fun relativeDue(daysLeft: Long, paid: Boolean): String = when {
        paid -> "оплачено"
        daysLeft == 0L -> "сегодня"
        daysLeft == 1L -> "завтра"
        daysLeft > 1L -> "через $daysLeft ${pluralDays(daysLeft)}"
        else -> "просрочен на ${-daysLeft} ${pluralDays(-daysLeft)}"
    }

    fun pluralDays(n: Long): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1L && mod100 != 11L -> "день"
            mod10 in 2..4 && mod100 !in 12..14 -> "дня"
            else -> "дней"
        }
    }
}
