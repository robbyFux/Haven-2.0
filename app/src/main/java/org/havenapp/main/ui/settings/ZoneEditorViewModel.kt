package org.havenapp.main.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.havenapp.main.detection.DetectionZone
import org.havenapp.main.sensor.CameraPosition
import org.havenapp.main.storage.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class ZoneEditorViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val zone: StateFlow<DetectionZone?> = settingsRepository.detectionZone
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val cameraPosition: StateFlow<CameraPosition> = settingsRepository.cameraPosition
        .stateIn(viewModelScope, SharingStarted.Eagerly, CameraPosition.BACK)

    fun saveZoneAndBack(zone: DetectionZone, onBack: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setDetectionZone(zone)
            withContext(Dispatchers.Main) { onBack() }
        }
    }

    fun clearZoneAndBack(onBack: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setDetectionZone(null)
            withContext(Dispatchers.Main) { onBack() }
        }
    }
}
