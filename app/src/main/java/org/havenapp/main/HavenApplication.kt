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
import org.havenapp.main.storage.AppLogger
import org.havenapp.main.storage.SettingsRepository
import java.io.File

@HiltAndroidApp
class HavenApplication : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun havenObjectDetector(): HavenObjectDetector
        fun settingsRepository(): SettingsRepository
        fun appLogger(): AppLogger
    }

    override fun onCreate() {
        super.onCreate()

        val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
        val settingsRepo = entryPoint.settingsRepository()

        // Wire AppLogger into MediaEncryptionManager so encrypt/decrypt ops appear
        // in the DiagnosticsScreen ring buffer.
        MediaEncryptionManager.logger = entryPoint.appLogger()

        // SEC-03: AppLockState starts locked=true by default (pessimistic lock).
        // HavenNavGraph auto-unlocks once DataStore confirms PIN is disabled.
        // No runBlocking needed here — avoids blocking the main thread during startup.

        // SEC-04: Auto-lock on background via ProcessLifecycleOwner.
        //
        // Design: cache pinEnabled + autoLockDelaySeconds in-memory by collecting the
        // DataStore flows persistently. onStop reads the cached values synchronously
        // (no suspension), so AppLockState.lock() is called immediately on the main
        // thread without any async race with onStart.
        //
        // lockJob: the delayed-lock timer. Cancelled by onStart if user returns in time.
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var cachedPinEnabled = false
        var cachedAutoLockDelay = 0
        // Keep DataStore values in sync throughout the process lifetime.
        appScope.launch {
            settingsRepo.pinEnabled.collect { cachedPinEnabled = it }
        }
        appScope.launch {
            settingsRepo.autoLockDelaySeconds.collect { cachedAutoLockDelay = it }
        }

        var lockJob: Job? = null
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (!cachedPinEnabled) return
                // Cancel any in-flight delayed lock from a previous background cycle.
                lockJob?.cancel()
                when {
                    cachedAutoLockDelay == 0 -> {
                        // Immediate lock: no delay, no coroutine needed.
                        AppLockState.lock()
                    }
                    cachedAutoLockDelay > 0 -> {
                        // Delayed lock: start a timer that fires after the configured delay.
                        lockJob = appScope.launch {
                            delay(cachedAutoLockDelay * 1_000L)
                            AppLockState.lock()
                        }
                    }
                    // cachedAutoLockDelay == -1 -> never auto-lock
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
