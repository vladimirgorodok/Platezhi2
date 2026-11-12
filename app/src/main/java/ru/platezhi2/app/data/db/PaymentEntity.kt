package ru.platezhi2.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.platezhi2.app.data.model.Payment
import ru.platezhi2.app.data.model.Recurrence

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val amountMinor: Long,
    val currency: String,
    val dueDateEpochDay: Long,
    val recurrence: String,
    val category: String,
    val logoPath: String?,
    val note: String,
    val paid: Boolean,
    val reminderEnabled: Boolean,
    val reminderDaysBefore: Int,
    val reminderHour: Int,
    val reminderMinute: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

fun PaymentEntity.toDomain(): Payment = Payment(
    id = id,
    title = title,
    amountMinor = amountMinor,
    currency = currency,
    dueDateEpochDay = dueDateEpochDay,
    recurrence = Recurrence.fromKey(recurrence),
    category = category,
    logoPath = logoPath,
    note = note,
    paid = paid,
    reminderEnabled = reminderEnabled,
    reminderDaysBefore = reminderDaysBefore,
    reminderHour = reminderHour,
    reminderMinute = reminderMinute,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Payment.toEntity(): PaymentEntity = PaymentEntity(
    id = id,
    title = title,
    amountMinor = amountMinor,
    currency = currency,
    dueDateEpochDay = dueDateEpochDay,
    recurrence = recurrence.key,
    category = category,
    logoPath = logoPath,
    note = note,
    paid = paid,
    reminderEnabled = reminderEnabled,
    reminderDaysBefore = reminderDaysBefore,
    reminderHour = reminderHour,
    reminderMinute = reminderMinute,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
