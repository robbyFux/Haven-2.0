package org.havenapp.main.ui.monitor

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.havenapp.main.MonitorService
import org.havenapp.main.detection.DetectionZone
import org.havenapp.main.sensor.MonitorState
import org.havenapp.main.storage.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val app: Application,
    settingsRepository: SettingsRepository,
) : AndroidViewModel(app) {

    val state: StateFlow<MonitorState> = MonitorService.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, MonitorState.IDLE)

    val countdownSeconds: StateFlow<Int> = MonitorService.countdownSeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val calibrationSecondsRemaining: StateFlow<Int> = MonitorService.calibrationSecondsRemaining
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val calibrationResults: StateFlow<MonitorService.CalibrationResults?> = MonitorService.calibrationResults
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val detectionZone: StateFlow<DetectionZone?> = settingsRepository.detectionZone
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun startMonitoring() {
        val intent = Intent(app, MonitorService::class.java).apply {
            action = MonitorService.ACTION_START
        }
        app.startForegroundService(intent)
    }

    fun stopMonitoring() {
        val intent = Intent(app, MonitorService::class.java).apply {
            action = MonitorService.ACTION_STOP
        }
        app.startService(intent)
    }
}
