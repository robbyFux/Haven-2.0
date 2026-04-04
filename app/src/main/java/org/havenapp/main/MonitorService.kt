package org.havenapp.main

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.PowerManager
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.havenapp.main.detection.HavenObjectDetector
import org.havenapp.main.media.CameraAnalyzer
import org.havenapp.main.media.ClipRecorder
import org.havenapp.main.security.MediaEncryptionManager
import org.havenapp.main.sensor.CameraPosition
import org.havenapp.main.sensor.FusedMotionMonitor
import org.havenapp.main.sensor.LightMonitor
import org.havenapp.main.sensor.MicrophoneMonitor
import org.havenapp.main.sensor.MonitorState
import org.havenapp.main.storage.EventRepository
import org.havenapp.main.storage.SettingsRepository
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : LifecycleService() {

    data class CalibrationResults(
        val motionNoiseFloor: Float? = null,
        val lightEmaBaseline: Float? = null,
    )

    companion object {
        private const val TAG = "MonitorService"
        const val ACTION_START = "haven.action.START"
        const val ACTION_STOP = "haven.action.STOP"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "haven_monitor"

        const val CALIBRATION_SECONDS_DEFAULT = 10

        /** Sekunden vor dem Stop, die beim Beenden verworfen werden (Nutzer läuft zum Gerät). */
        const val STOP_COOLDOWN_SECONDS = 30

        private val _state = MutableStateFlow(MonitorState.IDLE)
        val state = _state.asStateFlow()

        private val _countdownSeconds = MutableStateFlow(0)
        val countdownSeconds = _countdownSeconds.asStateFlow()

        private val _calibrationSecondsRemaining = MutableStateFlow(0)
        val calibrationSecondsRemaining = _calibrationSecondsRemaining.asStateFlow()

        private val _calibrationResults = MutableStateFlow<CalibrationResults?>(null)
        val calibrationResults = _calibrationResults.asStateFlow()
    }

    @Inject lateinit var eventRepository: EventRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var objectDetector: HavenObjectDetector
    @Inject lateinit var fusedMotionMonitor: FusedMotionMonitor
    @Inject lateinit var lightMonitor: LightMonitor
    @Inject lateinit var microphoneMonitor: MicrophoneMonitor
    @Inject lateinit var appLogger: org.havenapp.main.storage.AppLogger

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentEventId: Long? = null
    private var cameraAnalyzer: CameraAnalyzer? = null
    private var clipRecorder: ClipRecorder? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var monitoringJob: Job? = null
    private var clipDurationSecs: Int = 30

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            ACTION_START -> startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()

        monitoringJob = lifecycleScope.launch {
            val sensitivity = settingsRepository.sensitivity.first()
            val cameraPosition = settingsRepository.cameraPosition.first()
            val countdownSecs = settingsRepository.countdownSeconds.first()
            val calibrationMs = settingsRepository.calibrationSeconds.first() * 1_000L
            val detectionMode = settingsRepository.detectionMode.first()
            val detectionZone = settingsRepository.detectionZone.first()
            val motionEnabled = settingsRepository.motionEnabled.first()
            val lightEnabled = settingsRepository.lightEnabled.first()
            val micEnabled = settingsRepository.micEnabled.first()
            val cameraEnabled = settingsRepository.cameraEnabled.first()
            clipDurationSecs = settingsRepository.clipDurationSeconds.first()
            val mediaEncryptionEnabled = settingsRepository.mediaEncryptionEnabled.first()

            // --- Phase 1: Countdown ---
            if (countdownSecs > 0) {
                _state.value = MonitorState.COUNTDOWN
                for (remaining in countdownSecs downTo 1) {
                    _countdownSeconds.value = remaining
                    updateNotification()
                    delay(1_000L)
                }
                _countdownSeconds.value = 0
            }

            // --- Phase 2: Kalibrierung ---
            _state.value = MonitorState.CALIBRATING
            _calibrationResults.value = null
            _calibrationSecondsRemaining.value = (calibrationMs / 1000).toInt()
            updateNotification()

            // Countdown während Kalibrierung
            launch {
                val totalSecs = (calibrationMs / 1000).toInt()
                for (remaining in totalSecs downTo 0) {
                    _calibrationSecondsRemaining.value = remaining
                    delay(1_000L)
                }
            }

            // Sensor-Kalibrierungsergebnisse sammeln (nur aktivierte Sensoren)
            if (motionEnabled) launch {
                fusedMotionMonitor.noiseFloor.filterNotNull().first().let { nf ->
                    _calibrationResults.update { it?.copy(motionNoiseFloor = nf) ?: CalibrationResults(motionNoiseFloor = nf) }
                }
            }
            if (lightEnabled) launch {
                lightMonitor.emaBaseline.filterNotNull().first().let { baseline ->
                    _calibrationResults.update { it?.copy(lightEmaBaseline = baseline) ?: CalibrationResults(lightEmaBaseline = baseline) }
                }
            }

            appLogger.i(TAG, "Monitoring started: sensitivity=$sensitivity, mode=$detectionMode, camera=$cameraEnabled, motion=$motionEnabled, light=$lightEnabled, mic=$micEnabled")

            val analyzer: CameraAnalyzer? = if (cameraEnabled) {
                CameraAnalyzer(sensitivity, detectionMode, objectDetector, detectionZone)
                    .also { cameraAnalyzer = it; startCamera(it, cameraPosition) }
            } else null

            // Log TFLite status after CameraAnalyzer.init{} ran initialize() (if ML mode active)
            appLogger.i(TAG, "TFLite: requiresML=${detectionMode.requiresML}, available=${objectDetector.isAvailable}" +
                objectDetector.initError?.let { ", initError=$it" }.orEmpty())

            val sensorFlows = buildList {
                if (motionEnabled) add(fusedMotionMonitor.observe(sensitivity, calibrationMs))
                if (lightEnabled) add(lightMonitor.observe(sensitivity, calibrationMs))
                if (micEnabled) add(microphoneMonitor.observe(sensitivity, calibrationMs))
                if (analyzer != null) add(analyzer.events)
            }
            val sensorFlow = merge(*sensorFlows.toTypedArray())

            // Nach Kalibrierungszeit DB-Event öffnen und Zustand wechseln
            launch {
                delay(calibrationMs)
                currentEventId = eventRepository.openEvent()
                _state.value = MonitorState.ACTIVE
                updateNotification()
            }

            // --- Phase 3: Aktive Überwachung ---
            // Sensor-Flow läuft ab Kalibrierungsstart; currentEventId ist null
            // solange kalibriert wird → Events werden erst danach persistiert.
            sensorFlow.collect { trigger ->
                currentEventId?.let { eventId ->
                    // Suppress duplicate DB writes while a clip is recording (REC-04).
                    // The clip itself is the evidence for the trigger window; writing
                    // 100 identical microphone or light events to Room during a 30-second
                    // recording would flood the Timeline. Skip recording + clip start if
                    // ClipRecorder is actively recording. The CAMERA_VIDEO trigger written
                    // at clip-end is the single DB record for that recording window.
                    if (clipRecorder?.isRecording == true) return@collect

                    val triggerId = eventRepository.recordTrigger(eventId, trigger)
                    // Start clip on first trigger (REC-01); ClipRecorder guards against parallel clips (REC-03)
                    clipRecorder?.startClip(
                        context = this@MonitorService,
                        filesDir = filesDir,
                        durationSeconds = clipDurationSecs,
                    ) { rawClipPath ->
                        // Optionally encrypt the raw video file (SEC-01), then link path to trigger (REC-02).
                        // When mediaEncryptionEnabled is false the plain .mp4 path is stored directly.
                        lifecycleScope.launch {
                            val finalPath = if (mediaEncryptionEnabled) {
                                runCatching {
                                    MediaEncryptionManager.encryptInPlace(File(rawClipPath))
                                }.getOrElse { err ->
                                    appLogger.e(TAG, "Failed to encrypt clip: ${err.message}")
                                    rawClipPath  // fallback: store unencrypted path
                                }
                            } else {
                                rawClipPath
                            }
                            eventRepository.updateTriggerMediaPath(triggerId, finalPath)
                        }
                    }
                }
            }
        }
    }

    private fun stopMonitoring() {
        appLogger.i(TAG, "Monitoring stopped")
        monitoringJob?.cancel()
        monitoringJob = null

        lifecycleScope.launch {
            currentEventId?.let { id ->
                val cutoff = System.currentTimeMillis() - STOP_COOLDOWN_SECONDS * 1_000L
                eventRepository.discardTriggersSince(id, cutoff)
                eventRepository.closeEvent(id)
            }
            currentEventId = null
        }

        clipRecorder?.stopIfRecording()
        clipRecorder = null
        cameraAnalyzer?.reset()
        cameraAnalyzer = null
        _state.value = MonitorState.IDLE
        _countdownSeconds.value = 0
        _calibrationSecondsRemaining.value = 0
        _calibrationResults.value = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startCamera(analyzer: CameraAnalyzer, position: CameraPosition) {
        val cameraSelector = when (position) {
            CameraPosition.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            CameraPosition.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer) }

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.fromOrderedList(
                        listOf(Quality.HD, Quality.SD),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                    )
                )
                .build()
            val videoCaptureUseCase = VideoCapture.withOutput(recorder)

            // Attempt to bind both ImageAnalysis and VideoCapture together.
            // Falls back to ImageAnalysis-only on LEGACY hardware where VideoCapture
            // cannot be combined with other use cases.
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, videoCaptureUseCase)
            }.onSuccess {
                clipRecorder = ClipRecorder().also { it.attach(videoCaptureUseCase) }
            }.onFailure { err ->
                appLogger.w(TAG, "VideoCapture binding failed (LEGACY hardware?), disabling clip recording: ${err.message}")
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
                }
                clipRecorder = ClipRecorder().also { it.setUnavailable() }
            }
        }, mainExecutor)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Haven:MonitorWakeLock")
            .apply { acquire(12 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Haven Monitoring",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Aktive Überwachungssession" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentText = when (_state.value) {
            MonitorState.IDLE -> ""
            MonitorState.COUNTDOWN -> "Startet in ${_countdownSeconds.value}s"
            MonitorState.CALIBRATING -> "Kalibrierung läuft…"
            MonitorState.ACTIVE -> "Überwachung aktiv"
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Haven")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stopp", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        releaseWakeLock()
        _state.value = MonitorState.IDLE
        _countdownSeconds.value = 0
        super.onDestroy()
    }
}
