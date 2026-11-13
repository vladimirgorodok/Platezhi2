package ru.platezhi2.app.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.platezhi2.app.data.model.Recurrence
import java.time.LocalDate

/**
 * Тесты разбора файлов импорта.
 *
 * Отдельно проверяется файл старого приложения «Платежи»: там одна строка —
 * это один период оплаты, поэтому строки должны объединяться в одну карточку.
 */
class ImportParserTest {

    private val legacyHeader =
        "Название;Срок оплаты;Плановая сумма;Тип суммы;Оплачено;Фактическая дата;" +
            "Статус;Повтор;Следующая дата;Значок;Напомнить за, дней;Заметка"

    @Test
    fun `строки старого файла объединяются в одну карточку`() {
        val csv = "\uFEFF" + legacyHeader + "\n" +
            "Телефон;19.10.2025;550;Фиксированная;550;20.10.2025;Оплачен;Ежемесячно;19.11.2025;smartphone;3;\n" +
            "Телефон;19.11.2025;550;Фиксированная;0;;Запланирован;Ежемесячно;19.12.2025;smartphone;3;\n"

        val cards = ImportParser.parseCsv(csv)

        assertEquals(1, cards.size)
        val card = cards.first().card
        assertEquals("Телефон", card.title)
        assertEquals(55_000L, card.amountMinor)
        assertEquals(19, card.dueDay)
        assertEquals(Recurrence.MONTHLY, card.recurrence)
        assertEquals("📱", card.icon)
        assertTrue(card.reminderEnabled)
        assertEquals(3, card.reminderDaysBefore)

        val payments = cards.first().payments
        assertEquals(1, payments.size)
        assertEquals(55_000L, payments.first().amountMinor)
        assertEquals(LocalDate.of(2025, 10, 19).toEpochDay(), payments.first().periodEpochDay)
        assertEquals(LocalDate.of(2025, 10, 20).toEpochDay(), payments.first().paidAtEpochDay)
    }

    @Test
    fun `разные платежи не смешиваются`() {
        val csv = "\uFEFF" + legacyHeader + "\n" +
            "Телефон;19.10.2025;550;Фиксированная;550;20.10.2025;Оплачен;Ежемесячно;19.11.2025;smartphone;3;\n" +
            "Кредит;22.10.2025;14000;Фиксированная;14000;22.10.2025;Оплачен;Ежемесячно;22.11.2025;landmark;3;\n" +
            "Кредит;22.11.2025;14000;Фиксированная;0;;Запланирован;Ежемесячно;22.12.2025;landmark;3;\n" +
            "Штраф;12.11.2025;563;Фиксированная;563;;Оплачен;Не повторять;;triangle-alert;3;\n"

        val cards = ImportParser.parseCsv(csv)

        assertEquals(3, cards.size)
        assertEquals(listOf("Телефон", "Кредит", "Штраф"), cards.map { it.card.title })

        val credit = cards.first { it.card.title == "Кредит" }
        assertEquals(22, credit.card.dueDay)
        assertEquals(1_400_000L, credit.card.amountMinor)
        assertEquals("🏦", credit.card.icon)
        assertEquals(1, credit.payments.size)

        val fine = cards.first { it.card.title == "Штраф" }
        assertEquals(Recurrence.NONE, fine.card.recurrence)
        assertEquals("⚠️", fine.card.icon)
        assertEquals(1, fine.payments.size)
    }

    @Test
    fun `количество периодов не размножает карточки`() {
        val rows = (0 until 12).joinToString("\n") { month ->
            val due = LocalDate.of(2025, 10, 27).plusMonths(month.toLong())
            val status = if (month < 11) "Оплачен" else "Запланирован"
            val paid = if (status == "Оплачен") "3800" else "0"
            "Халва;${"%02d.%02d.%d".format(due.dayOfMonth, due.monthValue, due.year)};3800;" +
                "Фиксированная;$paid;;$status;Ежемесячно;;landmark;3;"
        }
        val csv = "\uFEFF" + legacyHeader + "\n" + rows + "\n"

        val cards = ImportParser.parseCsv(csv)

        assertEquals(1, cards.size)
        assertEquals(27, cards.first().card.dueDay)
        assertEquals(11, cards.first().payments.size)
    }

    @Test
    fun `оплаты подтягиваются к числу месяца карточки`() {
        // В старом файле оплата записана фактическим днём (22-е), а срок в
        // карточке — 2-е число. Период должен привестись к 2-му числу месяца.
        val csv = "\uFEFF" + legacyHeader + "\n" +
            "Совкомбанк;22.10.2025;14000;Фиксированная;14000;22.10.2025;Оплачен;Ежемесячно;22.11.2025;landmark;3;\n" +
            "Совкомбанк;22.11.2025;14000;Фиксированная;14000;22.11.2025;Оплачен;Ежемесячно;22.12.2025;landmark;3;\n" +
            "Совкомбанк;02.12.2025;14000;Фиксированная;0;;Запланирован;Ежемесячно;02.01.2026;landmark;3;\n"

        val cards = ImportParser.parseCsv(csv)

        assertEquals(1, cards.size)
        val card = cards.first().card
        assertEquals(2, card.dueDay)
        val periods = cards.first().payments
            .map { LocalDate.ofEpochDay(it.periodEpochDay) }
            .sorted()
        assertEquals(
            listOf(LocalDate.of(2025, 10, 2), LocalDate.of(2025, 11, 2)),
            periods,
        )
    }

    @Test
    fun `собственный формат читается с историей оплат`() {
        val csv = "Название;Сумма;Число месяца;Месяц;Повтор;Значок;Напоминание;" +
            "Напомнить за (дней);Время;Заметка;История оплат\n" +
            "Интернет;800;10;1;Ежемесячно;📶;да;2;09:00;заметка;" +
            "05.03.2026=800|05.04.2026=800\n"

        val cards = ImportParser.parseCsv(csv)

        assertEquals(1, cards.size)
        val card = cards.first().card
        assertEquals("Интернет", card.title)
        assertEquals(80_000L, card.amountMinor)
        assertEquals(10, card.dueDay)
        assertEquals("📶", card.icon)
        assertEquals(9, card.reminderHour)

        val payments = cards.first().payments
        assertEquals(2, payments.size)
        assertEquals(LocalDate.of(2026, 3, 10).toEpochDay(), payments[0].periodEpochDay)
        assertEquals(LocalDate.of(2026, 4, 10).toEpochDay(), payments[1].periodEpochDay)
        assertEquals(80_000L, payments[0].amountMinor)
    }

    @Test
    fun `файл с запятыми и кавычками`() {
        val csv = "Название,Сумма,Число месяца,Повтор,Значок,История оплат\n" +
            "\"Интернет, домашний\",800,10,Ежемесячно,📶,05.03.2026=800\n"

        val cards = ImportParser.parseCsv(csv)

        assertEquals(1, cards.size)
        assertEquals("Интернет, домашний", cards.first().card.title)
        assertEquals(1, cards.first().payments.size)
    }

    @Test
    fun `мусорный файл не превращается в записи`() {
        val cards = ImportParser.parseCsv("просто текст\nбез колонок\n")
        assertTrue(cards.isEmpty())
    }

    @Test
    fun `список платежей и оплат объединяется по названию`() {
        val csv = "\uFEFF" +
            "ID платежа;ID записи;Статус;Тип;Название;Сумма;Дата;Следующая дата;" +
            "День платежа;Дата окончания;Целевая сумма;Логотип;Комментарий;" +
            "Автор логотипа;Лицензия логотипа;Источник логотипа\n" +
            "card-1;record-1;Оплачен;Разовый;Телефон;550;2026-09-19;2026-09-19;;;;;;;\n" +
            "card-1;record-2;Оплачен;Разовый;Телефон;550;2026-10-19;2026-10-19;;;;;;;\n" +
            "card-1;;Запланирован;Разовый;Телефон;550;2026-11-19;2026-11-19;;;;;;;\n" +
            "legacy-7;legacy-7;Оплачен;Разовый;Штраф;563;2026-11-12;2026-11-12;;;;;;;\n"

        val cards = ImportParser.parseCsv(csv)

        assertEquals(2, cards.size)
        val phone = cards.first { it.card.title == "Телефон" }
        assertEquals(2, phone.payments.size)
        assertEquals(55_000L, phone.card.amountMinor)
        assertEquals(19, phone.card.dueDay)
        assertEquals(Recurrence.MONTHLY, phone.card.recurrence)

        val fine = cards.first { it.card.title == "Штраф" }
        assertEquals(1, fine.payments.size)
        assertEquals(Recurrence.NONE, fine.card.recurrence)
    }

    @Test
    fun `одинаковые записи одной карточки не дублируются`() {
        val csv = "ID платежа;ID записи;Статус;Название;Сумма;Дата;Следующая дата\n" +
            "card-1;r-1;Оплачен;Интернет;800;2026-10-05;2026-10-05\n" +
            "card-1;r-2;Оплачен;Интернет;800;2026-10-05;2026-10-05\n"

        val cards = ImportParser.parseCsv(csv)

        assertEquals(1, cards.size)
        assertEquals(1, cards.first().payments.size)
        assertEquals(80_000L, cards.first().payments.first().amountMinor)
    }

    @Test
    fun `значки старого приложения переводятся в эмодзи`() {
        assertEquals("📱", ImportParser.normalizeIcon("smartphone"))
        assertEquals("🏦", ImportParser.normalizeIcon("landmark"))
        assertEquals("🏠", ImportParser.normalizeIcon("house"))
        assertEquals("📶", ImportParser.normalizeIcon("router"))
        assertEquals("💡", ImportParser.normalizeIcon("plug-zap"))
        assertEquals("🧾", ImportParser.normalizeIcon("receipt-text"))
        assertEquals("📄", ImportParser.normalizeIcon("unknown-thing"))
        assertEquals("🙂", ImportParser.normalizeIcon("🙂"))
    }
}
