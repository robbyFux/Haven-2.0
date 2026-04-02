package org.havenapp.main.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.havenapp.main.BuildConfig
import org.havenapp.main.R
import org.havenapp.main.detection.DetectionMode
import org.havenapp.main.sensor.CameraPosition
import org.havenapp.main.sensor.Sensitivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenDiagnostics: () -> Unit,
    onOpenZoneEditor: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_settings)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSection(title = stringResource(R.string.settings_sensitivity_title)) {
                Column(modifier = Modifier.selectableGroup()) {
                    Sensitivity.entries.forEach { s ->
                        RadioRow(
                            label = s.label(),
                            selected = uiState.sensitivity == s,
                            onClick = { viewModel.setSensitivity(s) },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSection(title = stringResource(R.string.settings_camera_title)) {
                Column(modifier = Modifier.selectableGroup()) {
                    CameraPosition.entries.forEach { pos ->
                        RadioRow(
                            label = pos.label(),
                            selected = uiState.cameraPosition == pos,
                            onClick = { viewModel.setCameraPosition(pos) },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSection(title = stringResource(R.string.settings_detection_title)) {
                Column(modifier = Modifier.selectableGroup()) {
                    DetectionMode.entries.forEach { mode ->
                        val aiEnabled = !mode.requiresML || uiState.tfliteAvailable
                        RadioRow(
                            label = mode.label(),
                            selected = uiState.detectionMode == mode,
                            onClick = { viewModel.setDetectionMode(mode) },
                            enabled = aiEnabled,
                        )
                    }
                }
                if (!uiState.tfliteAvailable) {
                    Text(
                        text = stringResource(R.string.detection_ai_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFB300),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSection(title = stringResource(R.string.settings_sensors_title)) {
                SensorToggleRow(
                    label = stringResource(R.string.calibration_sensor_motion),
                    checked = uiState.motionEnabled,
                    onCheckedChange = { viewModel.setMotionEnabled(it) },
                )
                SensorToggleRow(
                    label = stringResource(R.string.calibration_sensor_light),
                    checked = uiState.lightEnabled,
                    onCheckedChange = { viewModel.setLightEnabled(it) },
                )
                SensorToggleRow(
                    label = stringResource(R.string.calibration_sensor_mic),
                    checked = uiState.micEnabled,
                    onCheckedChange = { viewModel.setMicEnabled(it) },
                )
                SensorToggleRow(
                    label = stringResource(R.string.calibration_sensor_camera),
                    checked = uiState.cameraEnabled,
                    onCheckedChange = { viewModel.setCameraEnabled(it) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSection(title = stringResource(R.string.settings_zone_title)) {
                val zone = uiState.detectionZone
                Text(
                    text = if (zone != null)
                        stringResource(
                            R.string.settings_zone_active,
                            ((zone.right - zone.left) * 100).toInt(),
                            ((zone.bottom - zone.top) * 100).toInt(),
                        )
                    else
                        stringResource(R.string.settings_zone_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenZoneEditor) {
                    Text(stringResource(R.string.settings_zone_edit))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSection(title = stringResource(R.string.settings_countdown_title)) {
                Column(modifier = Modifier.selectableGroup()) {
                    COUNTDOWN_OPTIONS.forEach { secs ->
                        RadioRow(
                            label = countdownLabel(secs),
                            selected = uiState.countdownSeconds == secs,
                            onClick = { viewModel.setCountdownSeconds(secs) },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSection(title = stringResource(R.string.settings_calibration_title)) {
                Column(modifier = Modifier.selectableGroup()) {
                    CALIBRATION_OPTIONS.forEach { secs ->
                        RadioRow(
                            label = calibrationLabel(secs),
                            selected = uiState.calibrationSeconds == secs,
                            onClick = { viewModel.setCalibrationSeconds(secs) },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSection(title = stringResource(R.string.settings_language_title)) {
                Column(modifier = Modifier.selectableGroup()) {
                    LANGUAGE_OPTIONS.forEach { (tag, label) ->
                        RadioRow(
                            label = label,
                            selected = uiState.languageTag == tag,
                            onClick = { viewModel.setLanguage(tag) },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSection(title = stringResource(R.string.settings_about_title)) {
                Text(
                    text = "${stringResource(R.string.about_version)}: ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Text(
                    text = "${stringResource(R.string.about_build)}: ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    Text(stringResource(R.string.settings_open_diagnostics))
                }
            }
        }
    }
}

private val COUNTDOWN_OPTIONS = listOf(0, 15, 30, 60, 90, 120)
private val CALIBRATION_OPTIONS = listOf(5, 10, 20, 30)
private val LANGUAGE_OPTIONS = listOf(
    "system" to "System default / Systemsprache",
    "en" to "English",
    "de" to "Deutsch",
)

@Composable
private fun countdownLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.countdown_no_delay)
    else -> stringResource(R.string.countdown_seconds, seconds)
}

@Composable
private fun calibrationLabel(seconds: Int): String = when (seconds) {
    5 -> stringResource(R.string.calibration_5s)
    10 -> stringResource(R.string.calibration_10s)
    20 -> stringResource(R.string.calibration_20s)
    30 -> stringResource(R.string.calibration_30s)
    else -> stringResource(R.string.countdown_seconds, seconds)
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    content()
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton, enabled = enabled)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun SensorToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Sensitivity.label(): String = when (this) {
    Sensitivity.OFF -> stringResource(R.string.sensitivity_off)
    Sensitivity.LOW -> stringResource(R.string.sensitivity_low)
    Sensitivity.MEDIUM -> stringResource(R.string.sensitivity_medium)
    Sensitivity.HIGH -> stringResource(R.string.sensitivity_high)
}

@Composable
private fun CameraPosition.label(): String = when (this) {
    CameraPosition.BACK -> stringResource(R.string.camera_back)
    CameraPosition.FRONT -> stringResource(R.string.camera_front)
}

@Composable
private fun DetectionMode.label(): String = when (this) {
    DetectionMode.MOTION_ONLY -> stringResource(R.string.detection_motion_only)
    DetectionMode.PERSON -> stringResource(R.string.detection_person)
    DetectionMode.PET -> stringResource(R.string.detection_pet)
    DetectionMode.VEHICLE -> stringResource(R.string.detection_vehicle)
    DetectionMode.ALL -> stringResource(R.string.detection_all)
}
