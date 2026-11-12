package ru.platezhi2.app.data.model

/**
 * Оплата по карточке — одна строка истории.
 *
 * [periodEpochDay] — срок периода, за который внесена оплата (epochDay).
 * [paidAtEpochDay] — фактическая дата оплаты (epochDay).
 * [amountMinor] — фактически уплаченная сумма в копейках.
 */
data class Payment(
    val id: Long = 0L,
    val cardId: Long = 0L,
    val periodEpochDay: Long = 0L,
    val paidAtEpochDay: Long = 0L,
    val amountMinor: Long = 0L,
    val createdAt: Long = 0L,
)
