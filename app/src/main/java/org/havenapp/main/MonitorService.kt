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
import kotlinx.coroutines.CompletableDeferred
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
import org.havenapp.main.sensor.ExpertThresholds
import org.havenapp.main.sensor.FusedMotionMonitor
import org.havenapp.main.sensor.LightMonitor
import org.havenapp.main.sensor.MicrophoneMonitor
import org.havenapp.main.sensor.MonitorState
import org.havenapp.main.sensor.RecentTriggerState
import org.havenapp.main.storage.EventRepository
import org.havenapp.main.storage.SettingsRepository
import java.util.concurrent.Executors
import javax.inject.Inject
import okhttp3.OkHttpClient
import org.havenapp.main.BuildConfig
import org.havenapp.main.notify.CloudChannel
import org.havenapp.main.notify.HavenAlertChannel
import org.havenapp.main.notify.MattermostChannel
import org.havenapp.main.notify.NotificationRouter
import org.havenapp.main.notify.NotificationRule
import org.havenapp.main.notify.PushoverChannel
import org.havenapp.main.notify.SignalRestChannel

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
    @Inject lateinit var notificationRouter: NotificationRouter
    @Inject lateinit var httpClient: OkHttpClient

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentEventId: Long? = null
    private var cameraAnalyzer: CameraAnalyzer? = null

    /**
     * Resolved by startCamera()'s cameraProviderFuture callback — either with a fully
     * attached ClipRecorder (isAvailable=true) or a setUnavailable() sentinel.
     * Using CompletableDeferred lets sensorFlow.collect await camera init without
     * blocking the main thread, eliminating the race where the first trigger fires
     * before the camera provider future resolves.
     *
     * Reset to a fresh CompletableDeferred each time startMonitoring() runs.
     * Remains incomplete (and awaiting it returns null via withTimeoutOrNull) when
     * cameraEnabled=false, so the collect block never hangs in that case.
     */
    private var clipRecorderDeferred = CompletableDeferred<ClipRecorder>()

    /** Holds the resolved ClipRecorder once startCamera()'s callback fires. Used by stopMonitoring() to stop any active recording without requiring @ExperimentalCoroutinesApi. */
    private var resolvedClipRecorder: ClipRecorder? = null

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
            val expert = settingsRepository.expertThresholds.first()
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
            val lightSuppressMotionSeconds = settingsRepository.lightSuppressMotionSeconds.first()

            // Notification settings snapshot (Phase 4)
            val signalEnabled = settingsRepository.signalEnabled.first()
            val signalServerUrl = settingsRepository.signalServerUrl.first()
            val signalSender = settingsRepository.signalSender.first()
            val signalRecipient = settingsRepository.signalRecipient.first()
            val signalBearerToken = settingsRepository.signalBearerToken.first()
            val mattermostEnabled = settingsRepository.mattermostEnabled.first()
            val mattermostWebhookUrl = settingsRepository.mattermostWebhookUrl.first()
            val heartbeatSignalMin = settingsRepository.heartbeatSignalMinutes.first()
            val heartbeatMattermostMin = settingsRepository.heartbeatMattermostMinutes.first()
            val pushoverEnabled = settingsRepository.pushoverEnabled.first()
            val pushoverUserKey = settingsRepository.pushoverUserKey.first()
            val pushoverAppToken = settingsRepository.pushoverAppToken.first()
            val heartbeatPushoverMin = settingsRepository.heartbeatPushoverMinutes.first()
            val cloudEnabled = settingsRepository.cloudEnabled.first()
            val cloudServerUrl = settingsRepository.cloudServerUrl.first()
            val cloudAppKey = settingsRepository.cloudAppKey.first()

            val notifRule = NotificationRule(
                minSeverity = settingsRepository.minSeverity.first(),
                cooldownMs = settingsRepository.cooldownMs.first(),
                triggerTypes = settingsRepository.notificationTriggerTypes.first(),
                attachMedia = settingsRepository.attachMedia.first(),
            )
            val notifChannels = buildList<HavenAlertChannel> {
                if (signalEnabled) {
                    add(SignalRestChannel(httpClient, signalServerUrl, signalSender, signalRecipient, signalBearerToken))
                }
                if (mattermostEnabled) {
                    add(MattermostChannel(httpClient, mattermostWebhookUrl))
                }
                if (pushoverEnabled) {
                    add(PushoverChannel(httpClient, pushoverAppToken, pushoverUserKey))
                }
                if (cloudEnabled) {
                    add(CloudChannel(httpClient, cloudServerUrl, cloudAppKey))
                }
            }
            notificationRouter.initialize(notifRule, notifChannels)

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

            appLogger.i(TAG, "Monitoring started: sensitivity=$sensitivity cameraMotionThreshold=${sensitivity.cameraMotionThreshold}, mode=$detectionMode, camera=$cameraEnabled, motion=$motionEnabled, light=$lightEnabled, mic=$micEnabled expertOverride=${expert != ExpertThresholds.DEFAULT}")

            // Reset deferred so a fresh await is available for this session.
            clipRecorderDeferred = CompletableDeferred()

            val analyzer: CameraAnalyzer? = if (cameraEnabled) {
                CameraAnalyzer(sensitivity, expert, detectionMode, objectDetector, detectionZone)
                    .also { cameraAnalyzer = it; startCamera(it, cameraPosition) }
            } else {
                android.util.Log.d("HAVEN_VIDEO", "cameraEnabled=false — startCamera() skipped, clipRecorder will remain null all session")
                null
            }

            // Log TFLite status after CameraAnalyzer.init{} ran initialize() (if ML mode active)
            appLogger.i(TAG, "TFLite: requiresML=${detectionMode.requiresML}, available=${objectDetector.isAvailable}" +
                objectDetector.initError?.let { ", initError=$it" }.orEmpty())

            val sensorFlows = buildList {
                if (motionEnabled) add(fusedMotionMonitor.observe(sensitivity, calibrationMs, expert))
                if (lightEnabled) add(lightMonitor.observe(sensitivity, calibrationMs, expert, lightSuppressMotionSeconds * 1000L))
                if (micEnabled) add(microphoneMonitor.observe(sensitivity, calibrationMs, expert))
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

            // Heartbeat coroutines (Phase 4, D-08/D-09/D-10)
            // Launched inside monitoringJob — auto-cancelled on stop via structured concurrency.
            val signalChannel = notifChannels.filterIsInstance<SignalRestChannel>().firstOrNull()
            val mattermostChannel = notifChannels.filterIsInstance<MattermostChannel>().firstOrNull()

            if (signalChannel != null && heartbeatSignalMin > 0) {
                launch {
                    // Wait for monitoring to become ACTIVE before first heartbeat
                    while (_state.value != MonitorState.ACTIVE) delay(500)
                    while (true) {
                        delay(heartbeatSignalMin * 60_000L)
                        val msg = "Haven alive - v${BuildConfig.VERSION_NAME} - ${_state.value} - ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
                        runCatching { signalChannel.sendHeartbeat(msg) }
                            .onFailure { appLogger.e(TAG, "Signal heartbeat failed: ${it.message}") }
                    }
                }
            }

            if (mattermostChannel != null && heartbeatMattermostMin > 0) {
                launch {
                    while (_state.value != MonitorState.ACTIVE) delay(500)
                    while (true) {
                        delay(heartbeatMattermostMin * 60_000L)
                        val msg = "Haven alive - v${BuildConfig.VERSION_NAME} - ${_state.value} - ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
                        runCatching { mattermostChannel.sendHeartbeat(msg) }
                            .onFailure { appLogger.e(TAG, "Mattermost heartbeat failed: ${it.message}") }
                    }
                }
            }

            val pushoverChannel = notifChannels.filterIsInstance<PushoverChannel>().firstOrNull()
            if (pushoverChannel != null && heartbeatPushoverMin > 0) {
                launch {
                    while (_state.value != MonitorState.ACTIVE) delay(500)
                    while (true) {
                        delay(heartbeatPushoverMin * 60_000L)
                        val msg = "Haven alive - v${BuildConfig.VERSION_NAME} - ${_state.value} - ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
                        runCatching { pushoverChannel.sendHeartbeat(msg) }
                            .onFailure { appLogger.e(TAG, "Pushover heartbeat failed: ${it.message}") }
                    }
                }
            }

            // --- Phase 3: Aktive Überwachung ---
            // Sensor-Flow läuft ab Kalibrierungsstart; currentEventId ist null
            // solange kalibriert wird → Events werden erst danach persistiert.
            sensorFlow.collect { trigger ->
                // Resolve the ClipRecorder for this trigger. If cameraEnabled=false the
                // deferred is never completed and withTimeoutOrNull returns null immediately
                // (timeout=0 → non-blocking poll). If cameraEnabled=true and camera init is
                // still in progress we wait up to 3 s for the provider callback to fire.
                val resolvedClipRecorder = if (clipRecorderDeferred.isCompleted) {
                    clipRecorderDeferred.await()
                } else if (cameraEnabled) {
                    kotlinx.coroutines.withTimeoutOrNull(3_000L) { clipRecorderDeferred.await() }
                } else {
                    null
                }
                android.util.Log.d("HAVEN_VIDEO", "sensorFlow trigger: type=${trigger.type} severity=${trigger.severity} currentEventId=$currentEventId clipRecorder=$resolvedClipRecorder clipRecorder.isAvailable=${resolvedClipRecorder?.isAvailable} clipRecorder.isRecording=${resolvedClipRecorder?.isRecording}")
                currentEventId?.let { eventId ->
                    RecentTriggerState.record(trigger.type)

                    // Suppress duplicate DB writes while a clip is recording (REC-04).
                    // The clip itself is the evidence for the trigger window; writing
                    // 100 identical microphone or light events to Room during a 30-second
                    // recording would flood the Timeline. Skip recording + clip start if
                    // ClipRecorder is actively recording. The CAMERA_VIDEO trigger written
                    // at clip-end is the single DB record for that recording window.
                    if (resolvedClipRecorder?.isRecording == true) {
                        android.util.Log.d("HAVEN_VIDEO", "Trigger suppressed — clip already recording")
                        return@collect
                    }

                    val triggerId = eventRepository.recordTrigger(eventId, trigger)
                    android.util.Log.d("HAVEN_VIDEO", "Trigger recorded to DB: triggerId=$triggerId type=${trigger.type}")

                    // Route to notification channels (Phase 4)
                    launch {
                        val frame = cameraAnalyzer?.lastJpegFrame
                        notificationRouter.route(trigger, frame)
                    }

                    // Start clip on first trigger (REC-01); ClipRecorder guards against parallel clips (REC-03)
                    if (resolvedClipRecorder == null) {
                        android.util.Log.e("HAVEN_VIDEO", "clipRecorder is null — cameraEnabled=$cameraEnabled; no video for trigger $triggerId")
                    } else {
                        android.util.Log.d("HAVEN_VIDEO", "Calling clipRecorder.startClip() for triggerId=$triggerId filesDir=${filesDir.absolutePath} duration=${clipDurationSecs}s")
                    }
                    resolvedClipRecorder?.startClip(
                        context = this@MonitorService,
                        filesDir = filesDir,
                        durationSeconds = clipDurationSecs,
                    ) { rawClipPath ->
                        android.util.Log.d("HAVEN_VIDEO", "onClipReady callback fired — rawClipPath=$rawClipPath triggerId=$triggerId")
                        // Optionally encrypt the raw video file (SEC-01), then link path to trigger (REC-02).
                        // When mediaEncryptionEnabled is false the plain .mp4 path is stored directly.
                        lifecycleScope.launch {
                            // Upload raw video to cloud channels before local encryption (NET-01).
                            // After encryptInPlace the original bytes are gone.
                            val rawFile = File(rawClipPath)
                            if (rawFile.exists()) {
                                android.util.Log.d("HAVEN_VIDEO", "Raw clip file exists: size=${rawFile.length()} bytes")
                                runCatching { rawFile.readBytes() }
                                    .onSuccess { bytes -> notificationRouter.uploadVideo(trigger, bytes) }
                                    .onFailure { appLogger.e(TAG, "Failed to read clip for upload: ${it.message}") }
                            } else {
                                android.util.Log.e("HAVEN_VIDEO", "Raw clip file does NOT exist at $rawClipPath — onClipReady was called but file is missing")
                            }

                            val finalPath = if (mediaEncryptionEnabled) {
                                runCatching {
                                    MediaEncryptionManager.encryptInPlace(rawFile)
                                }.getOrElse { err ->
                                    appLogger.e(TAG, "Failed to encrypt clip: ${err.message}")
                                    rawClipPath  // fallback: store unencrypted path
                                }
                            } else {
                                rawClipPath
                            }
                            android.util.Log.d("HAVEN_VIDEO", "Updating DB trigger $triggerId with finalPath=$finalPath")
                            eventRepository.updateTriggerMediaPath(triggerId, finalPath)
                        }
                    }
                }
            }
        }
    }

    private fun stopMonitoring() {
        appLogger.i(TAG, "Monitoring stopped")
        notificationRouter.reset()
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

        resolvedClipRecorder?.stopIfRecording()
        resolvedClipRecorder = null
        // Cancel any in-flight await so coroutines blocked on it unblock immediately.
        clipRecorderDeferred.cancel()
        RecentTriggerState.reset()
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
        android.util.Log.d("HAVEN_VIDEO", "startCamera() called — position=$position cameraSelector=$cameraSelector")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            android.util.Log.d("HAVEN_VIDEO", "cameraProviderFuture listener fired — attempting to get provider")
            val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrElse { err ->
                android.util.Log.e("HAVEN_VIDEO", "ProcessCameraProvider.get() THREW: ${err.message}", err)
                appLogger.e(TAG, "ProcessCameraProvider.get() failed: ${err.message}")
                val unavailable = ClipRecorder().also { it.setUnavailable() }
                resolvedClipRecorder = unavailable
                clipRecorderDeferred.complete(unavailable)
                return@addListener
            }
            android.util.Log.d("HAVEN_VIDEO", "cameraProvider obtained: $cameraProvider")

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer) }
            android.util.Log.d("HAVEN_VIDEO", "ImageAnalysis use-case built")

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.fromOrderedList(
                        listOf(Quality.HD, Quality.SD),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                    )
                )
                .build()
            val videoCaptureUseCase = VideoCapture.withOutput(recorder)
            android.util.Log.d("HAVEN_VIDEO", "VideoCapture use-case built: $videoCaptureUseCase")

            // Attempt to bind both ImageAnalysis and VideoCapture together.
            // Falls back to ImageAnalysis-only on LEGACY hardware where VideoCapture
            // cannot be combined with other use cases.
            android.util.Log.d("HAVEN_VIDEO", "Attempting bindToLifecycle(imageAnalysis + videoCapture)…")
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, videoCaptureUseCase)
            }.onSuccess {
                android.util.Log.d("HAVEN_VIDEO", "bindToLifecycle SUCCESS — imageAnalysis + videoCapture bound")
                appLogger.i(TAG, "Camera bound: imageAnalysis + videoCapture (${cameraSelector})")
                val recorder = ClipRecorder().also { it.attach(videoCaptureUseCase) }
                resolvedClipRecorder = recorder
                clipRecorderDeferred.complete(recorder)
                android.util.Log.d("HAVEN_VIDEO", "ClipRecorder created and attached — isAvailable=${recorder.isAvailable}")
            }.onFailure { err ->
                android.util.Log.e("HAVEN_VIDEO", "bindToLifecycle FAILED for imageAnalysis+videoCapture: ${err::class.simpleName} — ${err.message}", err)
                appLogger.w(TAG, "VideoCapture binding failed (LEGACY hardware?), disabling clip recording: ${err.message}")
                android.util.Log.d("HAVEN_VIDEO", "Attempting LEGACY fallback: bindToLifecycle(imageAnalysis only)…")
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
                }.onSuccess {
                    android.util.Log.d("HAVEN_VIDEO", "LEGACY fallback bindToLifecycle SUCCESS — imageAnalysis only")
                    appLogger.i(TAG, "Camera bound: imageAnalysis-only (LEGACY fallback)")
                }.onFailure { err2 ->
                    android.util.Log.e("HAVEN_VIDEO", "LEGACY fallback bindToLifecycle ALSO FAILED: ${err2::class.simpleName} — ${err2.message}", err2)
                    appLogger.e(TAG, "Camera binding FAILED entirely — no frames will arrive: ${err2.message}")
                }
                val unavailable = ClipRecorder().also { it.setUnavailable() }
                resolvedClipRecorder = unavailable
                clipRecorderDeferred.complete(unavailable)
                android.util.Log.d("HAVEN_VIDEO", "ClipRecorder set to unavailable — video recording disabled for this session")
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
