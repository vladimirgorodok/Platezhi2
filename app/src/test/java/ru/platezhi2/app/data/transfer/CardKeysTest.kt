package ru.platezhi2.app.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ru.platezhi2.app.data.model.Card
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.data.model.Recurrence

/**
 * Ключи дублей при импорте.
 *
 * Проверяем главное: загрузка копии (полная замена) не должна терять
 * разные платежи с похожими названиями, но полностью одинаковые — должна.
 */
class CardKeysTest {

    private fun card(
        title: String = "Совкомбанк",
        amountMinor: Long = 123_400L,
        dueDay: Int = 2,
        dueMonth: Int = 1,
        recurrence: Recurrence = Recurrence.MONTHLY,
        icon: String = "🏦",
        note: String = "",
        reminderEnabled: Boolean = false,
        reminderDaysBefore: Int = 1,
    ) = Card(
        title = title,
        amountMinor = amountMinor,
        dueDay = dueDay,
        dueMonth = dueMonth,
        recurrence = recurrence,
        icon = icon,
        note = note,
        reminderEnabled = reminderEnabled,
        reminderDaysBefore = reminderDaysBefore,
    )

    private fun parsed(card: Card, payments: Int = 0) = ImportParser.ParsedCard(
        card = card,
        payments = List(payments) { Payment(cardId = 1L, periodEpochDay = it.toLong(), paidAtEpochDay = it.toLong(), amountMinor = card.amountMinor) },
    )

    @Test
    fun `мягкий ключ не зависит от регистра и лишних пробелов`() {
        val first = CardKeys.loose(card(title = "Совкомбанк"))
        val second = CardKeys.loose(card(title = "  совкомбанк  "))

        assertEquals(first, second)
    }

    @Test
    fun `мягкий ключ различает повтор`() {
        assertNotEquals(
            CardKeys.loose(card(recurrence = Recurrence.MONTHLY)),
            CardKeys.loose(card(recurrence = Recurrence.YEARLY)),
        )
    }

    @Test
    fun `повторный импорт того же файла не создаёт вторые карточки`() {
        val keys = HashSet<String>()

        assertEquals(true, keys.add(CardKeys.loose(card())))
        // Вторая такая же карточка в другом файле — уже есть, добавлять не нужно.
        assertEquals(false, keys.add(CardKeys.loose(card(note = "оплата через приложение"))))
    }

    @Test
    fun `при замене разные заметки сохраняются`() {
        val first = CardKeys.full(parsed(card(note = "первая карта")))
        val second = CardKeys.full(parsed(card(note = "вторая карта")))

        assertNotEquals(first, second)
    }

    @Test
    fun `при замене разные значки сохраняются`() {
        assertNotEquals(
            CardKeys.full(parsed(card(icon = "🏦"))),
            CardKeys.full(parsed(card(icon = "💳"))),
        )
    }

    @Test
    fun `при замене разные напоминания сохраняются`() {
        assertNotEquals(
            CardKeys.full(parsed(card(reminderEnabled = false))),
            CardKeys.full(parsed(card(reminderEnabled = true, reminderDaysBefore = 3))),
        )
    }

    @Test
    fun `при замене полностью одинаковые карточки считаются одной`() {
        assertEquals(CardKeys.full(parsed(card())), CardKeys.full(parsed(card())))
        // Разное число оплат — это уже разные записи.
        assertNotEquals(CardKeys.full(parsed(card(), payments = 1)), CardKeys.full(parsed(card(), payments = 2)))
    }
}
