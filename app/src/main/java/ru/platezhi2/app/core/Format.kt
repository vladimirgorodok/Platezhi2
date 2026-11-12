package ru.platezhi2.app.core

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToLong

object Format {

    private val ru = Locale("ru", "RU")
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", ru)
    private val fullDate = DateTimeFormatter.ofPattern("d MMMM yyyy", ru)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", ru)

    fun money(amountMinor: Long, currency: String = "RUB"): String {
        val fmt = NumberFormat.getCurrencyInstance(ru)
        runCatching { fmt.currency = Currency.getInstance(currency) }
        return fmt.format(amountMinor / 100.0)
    }

    fun parseAmountToMinor(text: String): Long? {
        val cleaned = text.trim().replace("\u00A0", "").replace(" ", "").replace(",", ".")
        if (cleaned.isEmpty()) return 0L
        val value = cleaned.toDoubleOrNull() ?: return null
        return (value * 100.0).roundToLong()
    }

    fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(fullDate)

    fun dayMonth(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dayMonth)

    fun weekday(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(weekday)

    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    fun daysUntil(epochDay: Long): Long = epochDay - todayEpochDay()

    /** «сегодня», «завтра», «через 3 дня», «просрочен на 2 дня» */
    fun relativeDue(epochDay: Long, paid: Boolean): String {
        val d = daysUntil(epochDay)
        return when {
            paid -> "оплачено"
            d == 0L -> "сегодня"
            d == 1L -> "завтра"
            d > 1L -> "через $d ${pluralDays(d)}"
            else -> "просрочен на ${-d} ${pluralDays(-d)}"
        }
    }

    private fun pluralDays(n: Long): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1L && mod100 != 11L -> "день"
            mod10 in 2..4 && mod100 !in 12..14 -> "дня"
            else -> "дней"
        }
    }
}
