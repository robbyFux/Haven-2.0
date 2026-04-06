package org.havenapp.main.storage

import kotlinx.coroutines.flow.Flow
import org.havenapp.main.events.TriggerEvent
import org.havenapp.main.storage.dao.EventDao
import org.havenapp.main.storage.dao.EventTriggerDao
import org.havenapp.main.storage.dao.TriggerCount
import org.havenapp.main.storage.entity.EventEntity
import org.havenapp.main.storage.entity.EventTriggerEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val eventDao: EventDao,
    private val triggerDao: EventTriggerDao,
) {

    suspend fun openEvent(): Long {
        val entity = EventEntity(startTime = System.currentTimeMillis())
        return eventDao.insert(entity)
    }

    /** Records a trigger and returns the new row ID for subsequent media path updates. */
    suspend fun recordTrigger(eventId: Long, trigger: TriggerEvent): Long {
        val entity = EventTriggerEntity(
            eventId = eventId,
            type = trigger.type.id,
            timestamp = trigger.timestamp,
            sensorValue = trigger.sensorValue,
            mediaPath = trigger.mediaPath,
            severity = trigger.severity.ordinal,
        )
        return triggerDao.insert(entity)
    }

    /** Links a recorded video clip to the trigger that caused it (REC-02). */
    suspend fun updateTriggerMediaPath(triggerId: Long, mediaPath: String) {
        triggerDao.updateMediaPath(triggerId, mediaPath)
    }

    suspend fun closeEvent(eventId: Long) {
        eventDao.closeEvent(eventId, System.currentTimeMillis())
    }

    /** Löscht alle Trigger des Events, die nach [cutoffMs] aufgezeichnet wurden, und entfernt ihre Mediendateien. */
    suspend fun discardTriggersSince(eventId: Long, cutoffMs: Long) {
        val paths = triggerDao.getMediaPathsSince(eventId, cutoffMs)
        paths.forEach { path -> runCatching { java.io.File(path).delete() } }
        triggerDao.deleteSince(eventId, cutoffMs)
    }

    /** Löscht ein Event und (via ForeignKey CASCADE) alle zugehörigen Trigger sowie ihre Mediendateien. */
    suspend fun deleteEvent(eventId: Long) {
        val paths = triggerDao.getMediaPathsByEvent(eventId)
        paths.forEach { path -> runCatching { java.io.File(path).delete() } }
        eventDao.deleteById(eventId)
        // Room CASCADE deletes the event_triggers rows automatically
    }

    fun observeRecentEvents(): Flow<List<EventEntity>> = eventDao.observeRecent()

    fun observeTriggersForEvent(eventId: Long): Flow<List<EventTriggerEntity>> =
        triggerDao.observeByEvent(eventId)

    fun observeRecentTriggers(limit: Int = 100): Flow<List<EventTriggerEntity>> =
        triggerDao.observeRecent(limit)

    fun observeTriggerCountsPerEvent(): Flow<List<TriggerCount>> =
        triggerDao.observeCountsPerEvent()
}
