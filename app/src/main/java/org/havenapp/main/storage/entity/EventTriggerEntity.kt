package org.havenapp.main.storage.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein einzelner Sensor- oder Kamera-Auslöser, der einem Event zugeordnet ist.
 *
 * [type] entspricht [org.havenapp.main.events.TriggerType.id].
 * [severity] entspricht [org.havenapp.main.events.Severity.ordinal].
 */
@Entity(
    tableName = "event_triggers",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("eventId")],
)
data class EventTriggerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventId: Long,
    val type: Int,
    val timestamp: Long,
    val sensorValue: Float?,
    val mediaPath: String?,
    val severity: Int,
)
