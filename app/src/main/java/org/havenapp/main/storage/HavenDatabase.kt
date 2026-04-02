package org.havenapp.main.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import org.havenapp.main.storage.dao.EventDao
import org.havenapp.main.storage.dao.EventTriggerDao
import org.havenapp.main.storage.entity.EventEntity
import org.havenapp.main.storage.entity.EventTriggerEntity

@Database(
    entities = [EventEntity::class, EventTriggerEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class HavenDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun eventTriggerDao(): EventTriggerDao
}
