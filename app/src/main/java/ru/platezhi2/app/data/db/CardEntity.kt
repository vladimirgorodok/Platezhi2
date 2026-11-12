package ru.platezhi2.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.platezhi2.app.data.model.AppIcons
import ru.platezhi2.app.data.model.Card
import ru.platezhi2.app.data.model.Recurrence

/** Таблица карточек платежей. */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val amountMinor: Long,
    val dueDay: Int,
    val dueMonth: Int,
    val recurrence: String,
    val icon: String,
    val imagePath: String?,
    val note: String,
    val reminderEnabled: Boolean,
    val reminderDaysBefore: Int,
    val reminderHour: Int,
    val reminderMinute: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

fun CardEntity.toDomain(): Card = Card(
    id = id,
    title = title,
    amountMinor = amountMinor,
    dueDay = dueDay,
    dueMonth = dueMonth,
    recurrence = Recurrence.fromKey(recurrence),
    icon = AppIcons.normalize(icon),
    imagePath = imagePath,
    note = note,
    reminderEnabled = reminderEnabled,
    reminderDaysBefore = reminderDaysBefore,
    reminderHour = reminderHour,
    reminderMinute = reminderMinute,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Card.toEntity(): CardEntity = CardEntity(
    id = id,
    title = title,
    amountMinor = amountMinor,
    dueDay = dueDay,
    dueMonth = dueMonth,
    recurrence = recurrence.key,
    icon = AppIcons.normalize(icon),
    imagePath = imagePath,
    note = note,
    reminderEnabled = reminderEnabled,
    reminderDaysBefore = reminderDaysBefore,
    reminderHour = reminderHour,
    reminderMinute = reminderMinute,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
