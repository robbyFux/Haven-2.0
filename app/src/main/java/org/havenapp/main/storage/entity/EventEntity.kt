package org.havenapp.main.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Eine Überwachungssession mit mindestens einem Auslöser.
 * Wird beim ersten Trigger einer neuen Sitzung angelegt.
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
)
