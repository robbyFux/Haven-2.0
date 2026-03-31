package org.havenapp.main.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.havenapp.main.MonitorService
import org.havenapp.main.detection.DetectionMode
import org.havenapp.main.detection.DetectionZone
import org.havenapp.main.detection.HavenObjectDetector
import org.havenapp.main.sensor.CameraPosition
import org.havenapp.main.sensor.Sensitivity
import org.havenapp.main.storage.AppLogger
import org.havenapp.main.storage.EventRepository
import org.havenapp.main.storage.SettingsRepository
import org.havenapp.main.storage.entity.EventTriggerEntity
import javax.inject.Inject

data class DiagnosticsUiState(
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    val cameraPosition: CameraPosition = CameraPosition.BACK,
    val countdownSeconds: Int = 60,
    val calibrationSeconds: Int = 10,
    val detectionMode: DetectionMode = DetectionMode.MOTION_ONLY,
    val detectionZone: DetectionZone? = null,
    val tfliteAvailable: Boolean = false,
    val tfliteInitError: String? = null,
    val calibrationResults: MonitorService.CalibrationResults? = null,
    val recentTriggers: List<EventTriggerEntity> = emptyList(),
)

private data class PrimarySettings(
    val sensitivity: Sensitivity,
    val cameraPosition: CameraPosition,
    val countdownSeconds: Int,
    val detectionMode: DetectionMode,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    eventRepository: EventRepository,
    private val objectDetector: HavenObjectDetector,
    private val appLogger: AppLogger,
) : ViewModel() {

    val uiState: StateFlow<DiagnosticsUiState> = combine(
        combine(
            settingsRepository.sensitivity,
            settingsRepository.cameraPosition,
            settingsRepository.countdownSeconds,
            settingsRepository.detectionMode,
        ) { s, c, cd, mode -> PrimarySettings(s, c, cd, mode) },
        combine(
            settingsRepository.calibrationSeconds,
            settingsRepository.detectionZone,
            eventRepository.observeRecentTriggers(),
        ) { cal, zone, triggers -> Triple(cal, zone, triggers) },
        combine(
            MonitorService.calibrationResults,
            objectDetector.availabilityFlow,
        ) { calibResults, tfliteAvailable -> Pair(calibResults, tfliteAvailable) },
    ) { primary, (calibrationSeconds, detectionZone, triggers), (calibResults, tfliteAvailable) ->
        DiagnosticsUiState(
            sensitivity = primary.sensitivity,
            cameraPosition = primary.cameraPosition,
            countdownSeconds = primary.countdownSeconds,
            calibrationSeconds = calibrationSeconds,
            detectionMode = primary.detectionMode,
            detectionZone = detectionZone,
            tfliteAvailable = tfliteAvailable,
            tfliteInitError = objectDetector.initError,
            calibrationResults = calibResults,
            recentTriggers = triggers,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsUiState())

    val appLogs: StateFlow<List<AppLogger.Entry>> =
        appLogger.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearLogs() = appLogger.clear()
}
