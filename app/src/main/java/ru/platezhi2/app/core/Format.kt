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
    private val decimal = NumberFormat.getNumberInstance(ru).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    /** Формат даты старого приложения — для импорта. */
    val legacyDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Сумма без валюты: «1 234» или «1 234,56». */
    fun money(amountMinor: Long): String =
        if (amountMinor % 100L == 0L) integer.format(amountMinor / 100L)
        else decimal.format(amountMinor / 100.0)

    /** Сумма без копеек: «1 235». Для итогов в верхнем блоке. */
    fun moneyWhole(amountMinor: Long): String = integer.format(Math.round(amountMinor / 100.0))

    /** Значение для поля ввода суммы: «1234» или «1234,56». */
    fun amountInput(amountMinor: Long): String {
        if (amountMinor == 0L) return ""
        val rub = amountMinor / 100
        val kop = amountMinor % 100
        return if (kop == 0L) rub.toString() else "$rub,${kop.toString().padStart(2, '0')}"
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
