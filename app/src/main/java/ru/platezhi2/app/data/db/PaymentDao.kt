package ru.platezhi2.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Query("SELECT * FROM payments ORDER BY paidAtEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE cardId = :cardId ORDER BY paidAtEpochDay DESC, id DESC")
    fun observeForCard(cardId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE cardId = :cardId ORDER BY paidAtEpochDay DESC, id DESC")
    suspend fun getForCard(cardId: Long): List<PaymentEntity>

    @Query("SELECT * FROM payments")
    suspend fun getAll(): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PaymentEntity): Long

    @Update
    suspend fun update(entity: PaymentEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PaymentEntity>)

    @Delete
    suspend fun delete(entity: PaymentEntity)

    @Query("DELETE FROM payments WHERE cardId = :cardId AND periodEpochDay = :periodEpochDay")
    suspend fun deleteForPeriod(cardId: Long, periodEpochDay: Long)

    @Query("DELETE FROM payments WHERE cardId = :cardId")
    suspend fun deleteForCard(cardId: Long)

    @Query("DELETE FROM payments")
    suspend fun clear()
}
