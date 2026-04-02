package org.havenapp.main.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.havenapp.main.storage.HavenDatabase
import org.havenapp.main.storage.dao.EventDao
import org.havenapp.main.storage.dao.EventTriggerDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HavenDatabase =
        Room.databaseBuilder(context, HavenDatabase::class.java, "haven.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideEventDao(db: HavenDatabase): EventDao = db.eventDao()

    @Provides
    fun provideEventTriggerDao(db: HavenDatabase): EventTriggerDao = db.eventTriggerDao()
}
