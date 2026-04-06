package org.havenapp.main.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.havenapp.main.detection.DetectionMode
import org.havenapp.main.detection.DetectionZone
import org.havenapp.main.detection.HavenObjectDetector
import org.havenapp.main.events.Severity
import org.havenapp.main.events.TriggerType
import org.havenapp.main.security.AppLockState
import org.havenapp.main.security.PinHashManager
import org.havenapp.main.sensor.CameraPosition
import org.havenapp.main.sensor.Sensitivity
import org.havenapp.main.storage.AppLogger
import org.havenapp.main.storage.SettingsRepository
import javax.inject.Inject

data class SettingsUiState(
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    val cameraPosition: CameraPosition = CameraPosition.BACK,
    val countdownSeconds: Int = 60,
    val calibrationSeconds: Int = 10,
    val languageTag: String = "system",
    val detectionMode: DetectionMode = DetectionMode.MOTION_ONLY,
    val detectionZone: DetectionZone? = null,
    val tfliteAvailable: Boolean = false,
    val motionEnabled: Boolean = true,
    val lightEnabled: Boolean = true,
    val micEnabled: Boolean = true,
    val cameraEnabled: Boolean = true,
    val clipDurationSeconds: Int = 30,
    val mediaEncryptionEnabled: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    objectDetector: HavenObjectDetector,
    private val appLogger: AppLogger,
) : ViewModel() {

    init {
        // Initialize TFLite eagerly so ML detection modes are unlocked in the UI
        // as soon as the user opens Settings. Without this, availabilityFlow stays
        // false and PERSON/PET/VEHICLE/ALL modes remain permanently disabled.
        viewModelScope.launch(Dispatchers.IO) { objectDetector.initialize() }

        // Sync AppLogger log level from DataStore on startup (D-19)
        viewModelScope.launch {
            settingsRepository.logLevel.collect { level ->
                val logLevel = if (level == "DEBUG") AppLogger.LogLevel.DEBUG else AppLogger.LogLevel.NORMAL
                appLogger.setLogLevel(logLevel)
            }
        }
    }

    private val _languageTag = MutableStateFlow(currentLanguageTag())

    private fun currentLanguageTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) "system" else locales[0]!!.toLanguageTag()
    }

    private data class PrimarySettings(
        val sensitivity: Sensitivity,
        val cameraPosition: CameraPosition,
        val countdownSeconds: Int,
        val detectionMode: DetectionMode,
    )

    private data class SensorsConfig(
        val tfliteAvailable: Boolean,
        val motionEnabled: Boolean,
        val lightEnabled: Boolean,
        val micEnabled: Boolean,
        val cameraEnabled: Boolean,
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            settingsRepository.sensitivity,
            settingsRepository.cameraPosition,
            settingsRepository.countdownSeconds,
            settingsRepository.detectionMode,
        ) { sensitivity, cameraPosition, countdownSeconds, detectionMode ->
            PrimarySettings(sensitivity, cameraPosition, countdownSeconds, detectionMode)
        },
        combine(
            settingsRepository.calibrationSeconds,
            _languageTag,
            settingsRepository.detectionZone,
            settingsRepository.clipDurationSeconds,
            settingsRepository.mediaEncryptionEnabled,
        ) { cal, lang, zone, clipDur, encEnabled -> listOf(cal, lang, zone, clipDur, encEnabled) },
        combine(
            objectDetector.availabilityFlow,
            settingsRepository.motionEnabled,
            settingsRepository.lightEnabled,
            settingsRepository.micEnabled,
            settingsRepository.cameraEnabled,
        ) { tflite, motion, light, mic, camera ->
            SensorsConfig(tflite, motion, light, mic, camera)
        },
    ) { primary, secondary, sensors ->
        @Suppress("UNCHECKED_CAST")
        val secondaryList = secondary as List<Any?>
        val calibrationSeconds = secondaryList[0] as Int
        val languageTag = secondaryList[1] as String
        val detectionZone = secondaryList[2] as DetectionZone?
        val clipDurationSeconds = secondaryList[3] as Int
        val mediaEncryptionEnabled = secondaryList[4] as Boolean
        SettingsUiState(
            sensitivity = primary.sensitivity,
            cameraPosition = primary.cameraPosition,
            countdownSeconds = primary.countdownSeconds,
            calibrationSeconds = calibrationSeconds,
            languageTag = languageTag,
            detectionMode = primary.detectionMode,
            detectionZone = detectionZone,
            tfliteAvailable = sensors.tfliteAvailable,
            motionEnabled = sensors.motionEnabled,
            lightEnabled = sensors.lightEnabled,
            micEnabled = sensors.micEnabled,
            cameraEnabled = sensors.cameraEnabled,
            clipDurationSeconds = clipDurationSeconds,
            mediaEncryptionEnabled = mediaEncryptionEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setSensitivity(sensitivity: Sensitivity) {
        viewModelScope.launch { settingsRepository.setSensitivity(sensitivity) }
    }

    fun setCameraPosition(position: CameraPosition) {
        viewModelScope.launch { settingsRepository.setCameraPosition(position) }
    }

    fun setCountdownSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setCountdownSeconds(seconds) }
    }

    fun setCalibrationSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setCalibrationSeconds(seconds) }
    }

    fun setDetectionMode(mode: DetectionMode) {
        viewModelScope.launch { settingsRepository.setDetectionMode(mode) }
    }

    fun setMotionEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMotionEnabled(enabled) }
    }

    fun setLightEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLightEnabled(enabled) }
    }

    fun setMicEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMicEnabled(enabled) }
    }

    fun setCameraEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCameraEnabled(enabled) }
    }

    fun setClipDurationSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setClipDurationSeconds(seconds) }
    }

    fun setMediaEncryptionEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMediaEncryptionEnabled(enabled) }
    }

    fun setLanguage(tag: String) {
        val localeList = if (tag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
        _languageTag.value = tag
    }

    // PIN lock (SEC-03, SEC-04, SEC-05)

    /**
     * Nullable: `null` means DataStore hasn't emitted yet (loading state).
     * [HavenNavGraph] must wait for a non-null value before deciding to show PinLockScreen
     * or auto-unlock. This prevents the false-unlock window that occurred when the initial
     * value `false` caused the guard to pass before DataStore initialised.
     */
    val pinEnabled: StateFlow<Boolean?> = settingsRepository.pinEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pinHash: StateFlow<String?> = settingsRepository.pinHash
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pinSalt: StateFlow<String?> = settingsRepository.pinSalt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val autoLockDelaySeconds: StateFlow<Int> = settingsRepository.autoLockDelaySeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setPin(pin: String) {
        viewModelScope.launch {
            val result = PinHashManager.hashPin(pin)
            settingsRepository.setPinCredentials(result.hash, result.salt)
            settingsRepository.setPinEnabled(true)
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            settingsRepository.clearPinCredentials()
            AppLockState.unlock()
        }
    }

    fun setAutoLockDelay(seconds: Int) {
        viewModelScope.launch { settingsRepository.setAutoLockDelaySeconds(seconds) }
    }

    val lightSuppressMotionSeconds: StateFlow<Int> = settingsRepository.lightSuppressMotionSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 10)

    fun setLightSuppressMotionSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setLightSuppressMotionSeconds(seconds) }
    }

    // ── Notification settings (Phase 4) ─────────────────────────────────────

    val signalEnabled: StateFlow<Boolean> = settingsRepository.signalEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val signalServerUrl: StateFlow<String> = settingsRepository.signalServerUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val signalSender: StateFlow<String> = settingsRepository.signalSender
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val signalRecipient: StateFlow<String> = settingsRepository.signalRecipient
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val signalBearerToken: StateFlow<String> = settingsRepository.signalBearerToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val mattermostEnabled: StateFlow<Boolean> = settingsRepository.mattermostEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val mattermostWebhookUrl: StateFlow<String> = settingsRepository.mattermostWebhookUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val minSeverity: StateFlow<Severity> = settingsRepository.minSeverity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Severity.MEDIUM)

    val cooldownMs: StateFlow<Long> = settingsRepository.cooldownMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 60_000L)

    val notificationTriggerTypes: StateFlow<Set<TriggerType>> = settingsRepository.notificationTriggerTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val attachMedia: StateFlow<Boolean> = settingsRepository.attachMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val heartbeatSignalMinutes: StateFlow<Int> = settingsRepository.heartbeatSignalMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val heartbeatMattermostMinutes: StateFlow<Int> = settingsRepository.heartbeatMattermostMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val logLevelDebug: StateFlow<Boolean> = settingsRepository.logLevel
        .map { it == "DEBUG" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setSignalEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSignalEnabled(enabled) }
    }

    fun setSignalConfig(serverUrl: String, sender: String, recipient: String, bearerToken: String) {
        viewModelScope.launch { settingsRepository.setSignalConfig(serverUrl, sender, recipient, bearerToken) }
    }

    fun setMattermostEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMattermostEnabled(enabled) }
    }

    fun setMattermostWebhookUrl(url: String) {
        viewModelScope.launch { settingsRepository.setMattermostWebhookUrl(url) }
    }

    fun setMinSeverity(severity: Severity) {
        viewModelScope.launch { settingsRepository.setMinSeverity(severity) }
    }

    fun setCooldownMs(ms: Long) {
        viewModelScope.launch { settingsRepository.setCooldownMs(ms) }
    }

    fun setNotificationTriggerTypes(types: Set<TriggerType>) {
        viewModelScope.launch { settingsRepository.setNotificationTriggerTypes(types) }
    }

    fun setAttachMedia(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAttachMedia(enabled) }
    }

    fun setHeartbeatSignalMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setHeartbeatSignalMinutes(minutes) }
    }

    fun setHeartbeatMattermostMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setHeartbeatMattermostMinutes(minutes) }
    }

    fun setLogLevel(debug: Boolean) {
        val level = if (debug) "DEBUG" else "NORMAL"
        val logLevel = if (debug) AppLogger.LogLevel.DEBUG else AppLogger.LogLevel.NORMAL
        viewModelScope.launch { settingsRepository.setLogLevel(level) }
        appLogger.setLogLevel(logLevel)  // immediate effect (D-19)
    }
}
