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

    @Query("SELECT * FROM payments ORDER BY paid ASC, dueDateEpochDay ASC, id ASC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments")
    suspend fun getAll(): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PaymentEntity>)

    @Update
    suspend fun update(entity: PaymentEntity)

    @Delete
    suspend fun delete(entity: PaymentEntity)

    @Query("DELETE FROM payments")
    suspend fun clear()
}
