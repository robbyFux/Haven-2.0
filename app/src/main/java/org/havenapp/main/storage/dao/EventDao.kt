package org.havenapp.main.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.havenapp.main.storage.entity.EventEntity

@Dao
interface EventDao {

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Query("UPDATE events SET endTime = :endTime WHERE id = :id")
    suspend fun closeEvent(id: Long, endTime: Long)

    @Query("SELECT * FROM events ORDER BY startTime DESC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<EventEntity>>

    @Query("DELETE FROM events")
    suspend fun deleteAll()

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)
}
