package ru.platezhi2.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM cards ORDER BY dueDay ASC, id ASC")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CardEntity?

    @Query("SELECT * FROM cards ORDER BY id ASC")
    suspend fun getAll(): List<CardEntity>

    /**
     * Вставка новой карточки.
     *
     * ВАЖНО: только ABORT/IGNORE, но не REPLACE. `INSERT OR REPLACE` при
     * совпадении ключа удаляет старую строку, а вместе с ней по
     * `ON DELETE CASCADE` пропадают все оплаты этой карточки.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CardEntity): Long

    @Update
    suspend fun update(entity: CardEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<CardEntity>): List<Long>

    @Delete
    suspend fun delete(entity: CardEntity)

    @Query("DELETE FROM cards")
    suspend fun clear()
}
