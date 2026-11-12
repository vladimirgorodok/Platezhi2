package ru.platezhi2.app.data.model

/**
 * Карточка платежа — шаблон регулярного платежа.
 *
 * Сумма хранится в минорных единицах (копейках).
 * Срок оплаты задаётся числом месяца [dueDay] (1..31) и, для ежегодных
 * платежей, месяцем [dueMonth] (1..12).
 * Фактические оплаты хранятся отдельно в [Payment].
 */
data class Card(
    val id: Long = 0L,
    val title: String = "",
    val amountMinor: Long = 0L,
    val dueDay: Int = 1,
    val dueMonth: Int = 1,
    val recurrence: Recurrence = Recurrence.MONTHLY,
    val icon: String = AppIcons.DEFAULT,
    val imagePath: String? = null,
    val note: String = "",
    val reminderEnabled: Boolean = false,
    val reminderDaysBefore: Int = 1,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
