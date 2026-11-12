package ru.platezhi2.app.data.model

/** Доменная модель платежа. Сумма хранится в минорных единицах (копейках). */
data class Payment(
    val id: Long = 0L,
    val title: String = "",
    val amountMinor: Long = 0L,
    val currency: String = "RUB",
    val dueDateEpochDay: Long,
    val recurrence: Recurrence = Recurrence.NONE,
    val category: String = "other",
    val logoPath: String? = null,
    val note: String = "",
    val paid: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderDaysBefore: Int = 1,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
