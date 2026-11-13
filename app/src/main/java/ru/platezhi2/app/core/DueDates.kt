package ru.platezhi2.app.core

import ru.platezhi2.app.data.model.Card
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.data.model.Recurrence
import java.time.LocalDate
import java.time.YearMonth

/** Состояние срока оплаты карточки на конкретную дату. */
data class DueInfo(
    val date: LocalDate,
    val paid: Boolean,
    val overdue: Boolean,
    val daysLeft: Long,
)

/** Расчёт срока оплаты по числу месяца, периодичности и истории оплат. */
object DueDates {

    private fun safe(year: Int, month: Int, day: Int): LocalDate {
        val m = month.coerceIn(1, 12)
        val length = YearMonth.of(year, m).lengthOfMonth()
        return LocalDate.of(year, m, day.coerceIn(1, length))
    }

    /** Срок текущего периода; может быть в прошлом — тогда платёж просрочен. */
    fun currentPeriodDue(day: Int, month: Int, recurrence: Recurrence, today: LocalDate): LocalDate =
        when (recurrence) {
            Recurrence.YEARLY -> safe(today.year, month, day)
            else -> safe(today.year, today.monthValue, day)
        }

    /** Ближайший будущий срок (для напоминаний). */
    fun nextDue(day: Int, month: Int, recurrence: Recurrence, today: LocalDate): LocalDate =
        when (recurrence) {
            Recurrence.YEARLY -> {
                val d = safe(today.year, month, day)
                if (!d.isBefore(today)) d else safe(today.year + 1, month, day)
            }
            else -> {
                val d = safe(today.year, today.monthValue, day)
                if (!d.isBefore(today)) d
                else {
                    val nm = today.plusMonths(1)
                    safe(nm.year, nm.monthValue, day)
                }
            }
        }

    fun periodFor(card: Card, today: LocalDate = LocalDate.now()): LocalDate =
        currentPeriodDue(card.dueDay, card.dueMonth, card.recurrence, today)

    /**
     * Срок оплаты карточки в конкретном месяце — для фильтра по месяцам.
     * Возвращает null, если в этом месяце платежа нет (годовой платёж другого месяца).
     */
    fun dueInMonth(card: Card, month: YearMonth): LocalDate? = when (card.recurrence) {
        Recurrence.YEARLY -> if (month.monthValue == card.dueMonth) {
            safe(month.year, month.monthValue, card.dueDay)
        } else {
            null
        }
        else -> safe(month.year, month.monthValue, card.dueDay)
    }

    fun paidPeriods(payments: List<Payment>): Set<Long> =
        payments.mapTo(HashSet()) { it.periodEpochDay }

    fun info(card: Card, payments: List<Payment>, today: LocalDate = LocalDate.now()): DueInfo {
        val date = if (card.recurrence == Recurrence.NONE) {
            payments.minByOrNull { it.periodEpochDay }
                ?.let { LocalDate.ofEpochDay(it.periodEpochDay) }
                ?: currentPeriodDue(card.dueDay, card.dueMonth, card.recurrence, today)
        } else {
            currentPeriodDue(card.dueDay, card.dueMonth, card.recurrence, today)
        }

        val paid = when (card.recurrence) {
            Recurrence.NONE -> payments.isNotEmpty()
            else -> payments.any { it.periodEpochDay == date.toEpochDay() }
        }

        val daysLeft = date.toEpochDay() - today.toEpochDay()
        return DueInfo(
            date = date,
            paid = paid,
            overdue = !paid && daysLeft < 0L,
            daysLeft = daysLeft,
        )
    }

    /** Оплата за текущий период с плановой суммой карточки и сегодняшней датой. */
    fun newPayment(card: Card, amountMinor: Long = card.amountMinor, today: LocalDate = LocalDate.now()): Payment {
        val due = currentPeriodDue(card.dueDay, card.dueMonth, card.recurrence, today)
        return newPaymentForPeriod(card, due, amountMinor, today)
    }

    /** Оплата за указанный период (нужно для отметки оплаты в другом месяце). */
    fun newPaymentForPeriod(
        card: Card,
        period: LocalDate,
        amountMinor: Long = card.amountMinor,
        paidAt: LocalDate = LocalDate.now(),
    ): Payment = Payment(
        cardId = card.id,
        periodEpochDay = period.toEpochDay(),
        paidAtEpochDay = paidAt.toEpochDay(),
        amountMinor = amountMinor,
    )

    /** Период, к которому относится дата оплаты (для импорта истории). */
    fun periodForPaidDate(card: Card, paidAt: LocalDate): LocalDate =
        currentPeriodDue(card.dueDay, card.dueMonth, card.recurrence, paidAt)
}
