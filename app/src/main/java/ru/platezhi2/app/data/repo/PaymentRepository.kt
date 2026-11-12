package ru.platezhi2.app.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.platezhi2.app.data.db.CardDao
import ru.platezhi2.app.data.db.CardEntity
import ru.platezhi2.app.data.db.PaymentDao
import ru.platezhi2.app.data.db.PaymentEntity
import ru.platezhi2.app.data.db.toDomain
import ru.platezhi2.app.data.db.toEntity
import ru.platezhi2.app.data.model.Card
import ru.platezhi2.app.data.model.Payment

/**
 * Репозиторий карточек и оплат.
 *
 * Карточка — шаблон платежа, оплат по ней может быть много.
 */
class PaymentRepository(
    private val cardDao: CardDao,
    private val paymentDao: PaymentDao,
) {

    // ---- Карточки ----

    fun observeCards(): Flow<List<Card>> =
        cardDao.observeAll().map { list -> list.map(CardEntity::toDomain) }

    suspend fun getCard(id: Long): Card? = cardDao.getById(id)?.toDomain()

    suspend fun getCards(): List<Card> = cardDao.getAll().map(CardEntity::toDomain)

    suspend fun saveCard(card: Card): Long {
        val now = System.currentTimeMillis()
        val normalized = card.copy(
            createdAt = if (card.createdAt == 0L) now else card.createdAt,
            updatedAt = now,
        )
        return cardDao.upsert(normalized.toEntity())
    }

    suspend fun deleteCard(card: Card) {
        // Оплаты удаляются каскадно вместе с карточкой.
        cardDao.delete(card.toEntity())
    }

    // ---- Оплаты ----

    fun observePayments(): Flow<List<Payment>> =
        paymentDao.observeAll().map { list -> list.map(PaymentEntity::toDomain) }

    fun observePaymentsFor(cardId: Long): Flow<List<Payment>> =
        paymentDao.observeForCard(cardId).map { list -> list.map(PaymentEntity::toDomain) }

    suspend fun getPayments(): List<Payment> = paymentDao.getAll().map(PaymentEntity::toDomain)

    suspend fun getPaymentsFor(cardId: Long): List<Payment> =
        paymentDao.getForCard(cardId).map(PaymentEntity::toDomain)

    suspend fun addPayment(payment: Payment): Long {
        val now = System.currentTimeMillis()
        val normalized = payment.copy(createdAt = if (payment.createdAt == 0L) now else payment.createdAt)
        return paymentDao.insert(normalized.toEntity())
    }

    suspend fun deletePayment(payment: Payment) = paymentDao.delete(payment.toEntity())

    suspend fun deletePaymentForPeriod(cardId: Long, periodEpochDay: Long) =
        paymentDao.deleteForPeriod(cardId, periodEpochDay)

    // ---- Массовые операции (импорт) ----

    suspend fun clearAll() {
        paymentDao.clear()
        cardDao.clear()
    }

    /** Вставляет карточки и возвращает их новые идентификаторы в том же порядке. */
    suspend fun insertCards(cards: List<Card>): List<Long> {
        val now = System.currentTimeMillis()
        return cardDao.insertAll(
            cards.map { card ->
                card.copy(
                    id = 0L,
                    createdAt = if (card.createdAt == 0L) now else card.createdAt,
                    updatedAt = now,
                ).toEntity()
            }
        )
    }

    suspend fun insertPayments(payments: List<Payment>) {
        val now = System.currentTimeMillis()
        paymentDao.insertAll(
            payments.map { payment ->
                payment.copy(id = 0L, createdAt = if (payment.createdAt == 0L) now else payment.createdAt).toEntity()
            }
        )
    }
}
