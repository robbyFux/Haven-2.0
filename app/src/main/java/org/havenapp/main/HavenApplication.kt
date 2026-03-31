package org.havenapp.main

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.havenapp.main.detection.HavenObjectDetector

@HiltAndroidApp
class HavenApplication : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun havenObjectDetector(): HavenObjectDetector
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize TFLite at app startup so availability is known before any
        // screen renders. Runs on a background thread to avoid blocking the main thread.
        Thread {
            EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
                .havenObjectDetector()
                .initialize()
        }.apply { name = "tflite-init"; isDaemon = true }.start()
    }
}
