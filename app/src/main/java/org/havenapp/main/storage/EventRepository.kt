package org.havenapp.main.storage

import kotlinx.coroutines.flow.Flow
import org.havenapp.main.events.TriggerEvent
import org.havenapp.main.storage.dao.EventDao
import org.havenapp.main.storage.dao.EventTriggerDao
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

    suspend fun recordTrigger(eventId: Long, trigger: TriggerEvent) {
        val entity = EventTriggerEntity(
            eventId = eventId,
            type = trigger.type.id,
            timestamp = trigger.timestamp,
            sensorValue = trigger.sensorValue,
            mediaPath = trigger.mediaPath,
            severity = trigger.severity.ordinal,
        )
        triggerDao.insert(entity)
    }

    suspend fun closeEvent(eventId: Long) {
        eventDao.closeEvent(eventId, System.currentTimeMillis())
    }

    /** Löscht alle Trigger des Events, die nach [cutoffMs] aufgezeichnet wurden. */
    suspend fun discardTriggersSince(eventId: Long, cutoffMs: Long) {
        triggerDao.deleteSince(eventId, cutoffMs)
    }

    /** Löscht ein Event und (via ForeignKey CASCADE) alle zugehörigen Trigger. */
    suspend fun deleteEvent(eventId: Long) {
        eventDao.deleteById(eventId)
    }

    fun observeRecentEvents(): Flow<List<EventEntity>> = eventDao.observeRecent()

    fun observeTriggersForEvent(eventId: Long): Flow<List<EventTriggerEntity>> =
        triggerDao.observeByEvent(eventId)

    fun observeRecentTriggers(limit: Int = 100): Flow<List<EventTriggerEntity>> =
        triggerDao.observeRecent(limit)
}
