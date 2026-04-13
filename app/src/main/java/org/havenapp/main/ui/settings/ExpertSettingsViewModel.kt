package org.havenapp.main.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.havenapp.main.sensor.ExpertThresholdKind
import org.havenapp.main.sensor.ExpertThresholds
import org.havenapp.main.storage.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class ExpertSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val expertThresholds: StateFlow<ExpertThresholds> = settingsRepository.expertThresholds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpertThresholds.DEFAULT)

    fun setAccelMedium(value: Float?) = viewModelScope.launch {
        settingsRepository.setExpertThreshold(ExpertThresholdKind.ACCEL, value)
    }

    fun setMicMedium(value: Float?) = viewModelScope.launch {
        settingsRepository.setExpertThreshold(ExpertThresholdKind.MIC, value)
    }

    fun setLightMedium(value: Float?) = viewModelScope.launch {
        settingsRepository.setExpertThreshold(ExpertThresholdKind.LIGHT, value)
    }

    fun setCameraMedium(value: Float?) = viewModelScope.launch {
        settingsRepository.setExpertThreshold(ExpertThresholdKind.CAMERA, value)
    }

    fun resetAll() = viewModelScope.launch {
        settingsRepository.resetExpertThresholds()
    }
}
