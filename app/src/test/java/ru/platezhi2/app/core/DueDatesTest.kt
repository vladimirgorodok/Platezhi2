package ru.platezhi2.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.platezhi2.app.data.model.Card
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.data.model.Recurrence
import java.time.LocalDate
import java.time.YearMonth

/**
 * Сопоставление оплат с периодом месяца.
 *
 * Важный случай: в файлах старого приложения оплата записана фактическим днём
 * оплаты, который не совпадает с числом месяца в карточке. Такой платёж всё
 * равно должен закрывать период — иначе прошлые месяцы выглядят просроченными.
 */
class DueDatesTest {

    private val card = Card(
        id = 1L,
        title = "Совкомбанк",
        amountMinor = 1_400_000L,
        dueDay = 2,
        recurrence = Recurrence.MONTHLY,
    )

    private fun payment(period: LocalDate, paidAt: LocalDate = period) = Payment(
        id = period.toEpochDay(),
        cardId = 1L,
        periodEpochDay = period.toEpochDay(),
        paidAtEpochDay = paidAt.toEpochDay(),
        amountMinor = 1_400_000L,
    )

    @Test
    fun `точное совпадение по сроку`() {
        val p = payment(LocalDate.of(2025, 10, 2))
        val found = DueDates.paymentForPeriod(card, listOf(p), LocalDate.of(2025, 10, 2))
        assertEquals(p, found)
    }

    @Test
    fun `оплата другим числом того же месяца закрывает период`() {
        val p = payment(LocalDate.of(2025, 10, 22))
        val found = DueDates.paymentForPeriod(card, listOf(p), LocalDate.of(2025, 10, 2))
        assertNotNull(found)
        assertEquals(p, found)
    }

    @Test
    fun `оплата по дате внесения закрывает период`() {
        val p = payment(LocalDate.of(2025, 9, 27), paidAt = LocalDate.of(2025, 10, 3))
        val found = DueDates.paymentForPeriod(card, listOf(p), LocalDate.of(2025, 10, 2))
        assertNotNull(found)
    }

    @Test
    fun `оплата другого месяца период не закрывает`() {
        val p = payment(LocalDate.of(2025, 9, 22))
        assertNull(DueDates.paymentForPeriod(card, listOf(p), LocalDate.of(2025, 10, 2)))
    }

    @Test
    fun `точное совпадение приоритетнее совпадения по месяцу`() {
        val exact = payment(LocalDate.of(2025, 10, 2))
        val sameMonth = payment(LocalDate.of(2025, 10, 22))
        val found = DueDates.paymentForPeriod(card, listOf(sameMonth, exact), LocalDate.of(2025, 10, 2))
        assertEquals(exact, found)
    }

    @Test
    fun `разовый платёж подставляется только по точному сроку`() {
        val oneTime = card.copy(recurrence = Recurrence.NONE)
        val p = payment(LocalDate.of(2025, 10, 22))
        assertNull(DueDates.paymentForPeriod(oneTime, listOf(p), LocalDate.of(2025, 10, 2)))
        assertNotNull(DueDates.paymentForPeriod(oneTime, listOf(p), LocalDate.of(2025, 10, 22)))
    }

    @Test
    fun `годовой платёж закрывается оплатой в свой месяц`() {
        val yearly = card.copy(recurrence = Recurrence.YEARLY, dueDay = 15, dueMonth = 6)
        val p = payment(LocalDate.of(2025, 6, 20))
        assertNotNull(DueDates.paymentForPeriod(yearly, listOf(p), LocalDate.of(2025, 6, 15)))
        assertNull(DueDates.paymentForPeriod(yearly, listOf(p), LocalDate.of(2026, 6, 15)))
    }

    private fun cardCreatedOn(date: LocalDate) =
        card.copy(createdAt = date.toEpochDay() * 86_400_000L)

    @Test
    fun `новая карточка существует с месяца создания`() {
        val today = LocalDate.of(2026, 11, 13)
        val fresh = cardCreatedOn(today)
        assertEquals(YearMonth.of(2026, 11), DueDates.startMonth(fresh, emptyList(), today))
    }

    @Test
    fun `карточка без даты создания существует с текущего месяца`() {
        val today = LocalDate.of(2026, 11, 13)
        assertEquals(YearMonth.of(2026, 11), DueDates.startMonth(card, emptyList(), today))
    }

    @Test
    fun `перенесённая оплата показывает карточку с прошлого месяца`() {
        val today = LocalDate.of(2026, 11, 13)
        val imported = cardCreatedOn(today)
        val old = payment(LocalDate.of(2025, 12, 2), paidAt = LocalDate.of(2025, 12, 22))
        assertEquals(
            YearMonth.of(2025, 12),
            DueDates.startMonth(imported, listOf(old), today),
        )
    }

    @Test
    fun `оплата внесена раньше периода — карточка появляется по дате внесения`() {
        val today = LocalDate.of(2026, 11, 13)
        val imported = cardCreatedOn(today)
        val early = payment(LocalDate.of(2026, 11, 10), paidAt = LocalDate.of(2026, 10, 30))
        assertEquals(YearMonth.of(2026, 10), DueDates.startMonth(imported, listOf(early), today))
    }

    @Test
    fun `новая карточка не показывается в прошлых месяцах`() {
        val today = LocalDate.of(2026, 11, 13)
        val fresh = cardCreatedOn(today)
        val start = DueDates.startMonth(fresh, emptyList(), today)
        assertTrue(start > YearMonth.of(2026, 8))
        assertFalse(start > YearMonth.of(2026, 11))
    }

    @Test
    fun `срок, прошедший до создания карточки, не просрочен`() {
        val today = LocalDate.of(2026, 11, 13)
        // Яндекс: подписка оформлена 13 ноября, срок оплаты — 10-е число.
        val fresh = cardCreatedOn(today).copy(dueDay = 10)
        assertFalse(
            DueDates.isOverdue(fresh, emptyList(), LocalDate.of(2026, 11, 10), paid = false, today = today)
        )
        // Следующий срок уже настоящая просрочка.
        assertTrue(
            DueDates.isOverdue(
                fresh, emptyList(), LocalDate.of(2026, 12, 10), paid = false,
                today = LocalDate.of(2027, 1, 5),
            )
        )
    }

    @Test
    fun `пропуск после первой оплаты остаётся просрочкой`() {
        val today = LocalDate.of(2026, 11, 13)
        val imported = cardCreatedOn(today)
        val july = payment(LocalDate.of(2026, 7, 22))
        assertTrue(
            DueDates.isOverdue(
                imported, listOf(july), LocalDate.of(2026, 8, 22), paid = false, today = today,
            )
        )
        assertFalse(
            DueDates.isOverdue(
                imported, listOf(july), LocalDate.of(2026, 7, 22), paid = true, today = today,
            )
        )
    }
}
