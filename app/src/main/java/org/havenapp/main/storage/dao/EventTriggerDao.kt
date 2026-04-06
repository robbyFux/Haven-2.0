package org.havenapp.main.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.havenapp.main.storage.entity.EventTriggerEntity

/** Per-event trigger count returned by [EventTriggerDao.observeCountsPerEvent]. */
data class TriggerCount(val eventId: Long, val count: Int)

@Dao
interface EventTriggerDao {

    @Insert
    suspend fun insert(trigger: EventTriggerEntity): Long

    @Query("SELECT * FROM event_triggers WHERE eventId = :eventId ORDER BY timestamp ASC")
    fun observeByEvent(eventId: Long): Flow<List<EventTriggerEntity>>

    /** Returns a live count of triggers grouped by event, for all events. */
    @Query("SELECT eventId, COUNT(*) AS count FROM event_triggers GROUP BY eventId")
    fun observeCountsPerEvent(): Flow<List<TriggerCount>>

    @Query("SELECT * FROM event_triggers ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<EventTriggerEntity>>

    @Query("DELETE FROM event_triggers WHERE eventId = :eventId")
    suspend fun deleteByEvent(eventId: Long)

    @Query("DELETE FROM event_triggers WHERE eventId = :eventId AND timestamp >= :cutoffMs")
    suspend fun deleteSince(eventId: Long, cutoffMs: Long)

    @Query("UPDATE event_triggers SET mediaPath = :mediaPath WHERE id = :triggerId")
    suspend fun updateMediaPath(triggerId: Long, mediaPath: String)

    /** Returns all non-null media paths for triggers belonging to [eventId]. */
    @Query("SELECT mediaPath FROM event_triggers WHERE eventId = :eventId AND mediaPath IS NOT NULL")
    suspend fun getMediaPathsByEvent(eventId: Long): List<String>

    /** Returns all non-null media paths for triggers in [eventId] at or after [cutoffMs]. */
    @Query("SELECT mediaPath FROM event_triggers WHERE eventId = :eventId AND timestamp >= :cutoffMs AND mediaPath IS NOT NULL")
    suspend fun getMediaPathsSince(eventId: Long, cutoffMs: Long): List<String>
}
