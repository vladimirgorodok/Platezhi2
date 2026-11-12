package ru.platezhi2.app.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.platezhi2.app.data.db.PaymentDao
import ru.platezhi2.app.data.db.PaymentEntity
import ru.platezhi2.app.data.db.toDomain
import ru.platezhi2.app.data.db.toEntity
import ru.platezhi2.app.data.model.Payment

class PaymentRepository(private val dao: PaymentDao) {

    fun observeAll(): Flow<List<Payment>> =
        dao.observeAll().map { list -> list.map(PaymentEntity::toDomain) }

    suspend fun get(id: Long): Payment? = dao.getById(id)?.toDomain()

    suspend fun getAll(): List<Payment> = dao.getAll().map(PaymentEntity::toDomain)

    suspend fun save(payment: Payment): Long {
        val now = System.currentTimeMillis()
        val normalized = payment.copy(
            createdAt = if (payment.createdAt == 0L) now else payment.createdAt,
            updatedAt = now,
        )
        return dao.upsert(normalized.toEntity())
    }

    suspend fun setPaid(id: Long, paid: Boolean) {
        val current = dao.getById(id) ?: return
        dao.update(current.copy(paid = paid, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(payment: Payment) = dao.delete(payment.toEntity())

    suspend fun replaceAll(items: List<Payment>) {
        dao.clear()
        dao.insertAll(items.map { it.toEntity() })
    }
}
