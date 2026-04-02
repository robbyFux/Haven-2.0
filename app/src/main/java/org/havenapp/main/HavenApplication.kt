package org.havenapp.main

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.havenapp.main.detection.HavenObjectDetector
import org.havenapp.main.security.AppLockState
import org.havenapp.main.security.MediaEncryptionManager
import org.havenapp.main.storage.SettingsRepository
import java.io.File

@HiltAndroidApp
class HavenApplication : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun havenObjectDetector(): HavenObjectDetector
        fun settingsRepository(): SettingsRepository
    }

    override fun onCreate() {
        super.onCreate()

        val settingsRepo = EntryPointAccessors
            .fromApplication(this, AppEntryPoint::class.java)
            .settingsRepository()

        // SEC-03: Lock on cold start if PIN is enabled.
        val pinEnabled = runBlocking { settingsRepo.pinEnabled.first() }
        if (pinEnabled) {
            AppLockState.lock()
        }

        // SEC-04: Auto-lock on background via ProcessLifecycleOwner.
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var lockJob: Job? = null
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                val currentPinEnabled = runBlocking { settingsRepo.pinEnabled.first() }
                val currentDelay = runBlocking { settingsRepo.autoLockDelaySeconds.first() }
                if (!currentPinEnabled) return
                when {
                    currentDelay == 0 -> AppLockState.lock()
                    currentDelay > 0 -> {
                        lockJob?.cancel()
                        lockJob = appScope.launch {
                            delay(currentDelay * 1_000L)
                            AppLockState.lock()
                        }
                    }
                    // currentDelay == -1 -> never auto-lock
                }
            }
            override fun onStart(owner: LifecycleOwner) {
                lockJob?.cancel()
            }
        })

        // Initialize TFLite at app startup so availability is known before any
        // screen renders. Runs on a background thread to avoid blocking the main thread.
        Thread {
            EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
                .havenObjectDetector()
                .initialize()
        }.apply { name = "tflite-init"; isDaemon = true }.start()

        // Encrypt any existing unencrypted media files (SEC-02).
        // One-shot migration: runs only if the migration flag is not yet set.
        Thread {
            val settingsRepository = EntryPointAccessors
                .fromApplication(this, AppEntryPoint::class.java)
                .settingsRepository()
            runBlocking {
                if (!settingsRepository.mediaEncryptedV1.first()) {
                    filesDir.listFiles()
                        ?.filter { it.extension == "mp4" && !File(it.absolutePath + ".enc").exists() }
                        ?.forEach { plainFile ->
                            runCatching { MediaEncryptionManager.encryptInPlace(plainFile) }
                        }
                    settingsRepository.setMediaEncryptedV1(true)
                }
            }
        }.apply { name = "media-encryption-migration"; isDaemon = true }.start()
    }
}
