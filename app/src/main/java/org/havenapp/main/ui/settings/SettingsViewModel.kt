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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.havenapp.main.detection.DetectionMode
import org.havenapp.main.detection.DetectionZone
import org.havenapp.main.detection.HavenObjectDetector
import org.havenapp.main.sensor.CameraPosition
import org.havenapp.main.sensor.Sensitivity
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    objectDetector: HavenObjectDetector,
) : ViewModel() {

    init {
        // Initialize TFLite eagerly so ML detection modes are unlocked in the UI
        // as soon as the user opens Settings. Without this, availabilityFlow stays
        // false and PERSON/PET/VEHICLE/ALL modes remain permanently disabled.
        viewModelScope.launch(Dispatchers.IO) { objectDetector.initialize() }
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
        ) { cal, lang, zone -> Triple(cal, lang, zone) },
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
        val (calibrationSeconds, languageTag, detectionZone) = secondary
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

    fun setLanguage(tag: String) {
        val localeList = if (tag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
        _languageTag.value = tag
    }
}
