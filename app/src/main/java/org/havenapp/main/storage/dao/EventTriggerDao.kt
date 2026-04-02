package org.havenapp.main.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.havenapp.main.storage.entity.EventTriggerEntity

@Dao
interface EventTriggerDao {

    @Insert
    suspend fun insert(trigger: EventTriggerEntity): Long

    @Query("SELECT * FROM event_triggers WHERE eventId = :eventId ORDER BY timestamp ASC")
    fun observeByEvent(eventId: Long): Flow<List<EventTriggerEntity>>

    @Query("SELECT * FROM event_triggers ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<EventTriggerEntity>>

    @Query("DELETE FROM event_triggers WHERE eventId = :eventId")
    suspend fun deleteByEvent(eventId: Long)

    @Query("DELETE FROM event_triggers WHERE eventId = :eventId AND timestamp >= :cutoffMs")
    suspend fun deleteSince(eventId: Long, cutoffMs: Long)
}
