package ru.platezhi2.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.platezhi2.app.data.model.Payment

/** Таблица оплат: много строк на одну карточку. */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cardId")],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cardId: Long,
    val periodEpochDay: Long,
    val paidAtEpochDay: Long,
    val amountMinor: Long,
    val createdAt: Long,
)

fun PaymentEntity.toDomain(): Payment = Payment(
    id = id,
    cardId = cardId,
    periodEpochDay = periodEpochDay,
    paidAtEpochDay = paidAtEpochDay,
    amountMinor = amountMinor,
    createdAt = createdAt,
)

fun Payment.toEntity(): PaymentEntity = PaymentEntity(
    id = id,
    cardId = cardId,
    periodEpochDay = periodEpochDay,
    paidAtEpochDay = paidAtEpochDay,
    amountMinor = amountMinor,
    createdAt = createdAt,
)
