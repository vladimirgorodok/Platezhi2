package ru.platezhi2.app.data.transfer

import ru.platezhi2.app.data.model.Card

/**
 * Ключи карточек для отсева дублей при импорте.
 *
 * Вынесено отдельно: от этих ключей зависит, не потеряются ли платежи
 * при загрузке копии, поэтому поведение покрыто тестами.
 */
internal object CardKeys {

    /**
     * Мягкий ключ. Используется, когда данные добавляются к существующим:
     * повторный импорт того же файла не должен создавать вторые карточки.
     */
    fun loose(card: Card): String = buildString {
        append(normalizeTitle(card.title)).append('|')
        append(card.amountMinor).append('|')
        append(card.dueDay).append('|')
        append(card.dueMonth).append('|')
        append(card.recurrence.key)
    }

    /**
     * Полный ключ. Используется при полной замене данных: отбрасываем только
     * полностью одинаковые карточки, чтобы не потерять разные платежи
     * с похожими названиями.
     */
    fun full(imported: ImportParser.ParsedCard): String = buildString {
        val card = imported.card
        append(loose(card)).append('|')
        append(card.icon).append('|')
        append(card.note).append('|')
        append(card.reminderEnabled).append('|')
        append(card.reminderDaysBefore).append('|')
        append(card.reminderHour).append('|')
        append(card.reminderMinute).append('|')
        append(imported.payments.size)
    }

    private fun normalizeTitle(title: String): String =
        title.trim().lowercase().replace(Regex("\\s+"), " ")
}
