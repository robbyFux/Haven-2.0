package org.havenapp.main.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val pinEnabled by viewModel.pinEnabled.collectAsStateWithLifecycle()
    val pinHash by viewModel.pinHash.collectAsStateWithLifecycle()
    val autoLockDelaySeconds by viewModel.autoLockDelaySeconds.collectAsStateWithLifecycle()

    // TODO: Plan 05 wires to SettingsViewModel.logLevelDebug
    var logLevelDebug by remember { mutableStateOf(false) }

    // Dialog state for PIN setup / change
    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogIsChange by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(0.dp))

            // Card 1 — Detection
            CategoryCard(title = stringResource(R.string.settings_cat_detection)) {
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

                SettingsSection(title = stringResource(R.string.settings_light_suppress_title)) {
                    val lightSuppressSeconds by viewModel.lightSuppressMotionSeconds.collectAsStateWithLifecycle()
                    Column(modifier = Modifier.selectableGroup()) {
                        LIGHT_SUPPRESS_OPTIONS.forEach { secs ->
                            RadioRow(
                                label = lightSuppressLabel(secs),
                                selected = lightSuppressSeconds == secs,
                                onClick = { viewModel.setLightSuppressMotionSeconds(secs) },
                            )
                        }
                    }
                }
            }

            // Card 2 — Recording
            CategoryCard(title = stringResource(R.string.settings_cat_recording)) {
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

                SettingsSection(title = stringResource(R.string.settings_clip_duration_title)) {
                    Column(modifier = Modifier.selectableGroup()) {
                        CLIP_DURATION_OPTIONS.forEach { secs ->
                            RadioRow(
                                label = clipDurationLabel(secs),
                                selected = uiState.clipDurationSeconds == secs,
                                onClick = { viewModel.setClipDurationSeconds(secs) },
                            )
                        }
                    }
                }
            }

            // Card 3 — Security
            CategoryCard(title = stringResource(R.string.settings_cat_security)) {
                // PIN lock section (SEC-03, SEC-04, SEC-05)
                SettingsSection(title = stringResource(R.string.settings_pin_title)) {
                    SensorToggleRow(
                        label = stringResource(R.string.settings_pin_enable),
                        checked = pinEnabled == true,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // Enabling: open setup dialog if no hash stored yet
                                pinDialogIsChange = false
                                showPinDialog = true
                            } else {
                                viewModel.clearPin()
                            }
                        },
                    )
                    if (pinEnabled == true && pinHash != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            pinDialogIsChange = true
                            showPinDialog = true
                        }) {
                            Text(stringResource(R.string.settings_pin_change))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_autolock_title),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                        Column(modifier = Modifier.selectableGroup()) {
                            AutoLockOption(
                                label = stringResource(R.string.settings_autolock_immediate),
                                selected = autoLockDelaySeconds == 0,
                                onClick = { viewModel.setAutoLockDelay(0) },
                            )
                            AutoLockOption(
                                label = stringResource(R.string.settings_autolock_30s),
                                selected = autoLockDelaySeconds == 30,
                                onClick = { viewModel.setAutoLockDelay(30) },
                            )
                            AutoLockOption(
                                label = stringResource(R.string.settings_autolock_never),
                                selected = autoLockDelaySeconds == -1,
                                onClick = { viewModel.setAutoLockDelay(-1) },
                            )
                        }
                    }
                }

                // Security section
                SettingsSection(title = stringResource(R.string.settings_security_title)) {
                    SensorToggleRow(
                        label = stringResource(R.string.settings_media_encryption_label),
                        checked = uiState.mediaEncryptionEnabled,
                        onCheckedChange = { viewModel.setMediaEncryptionEnabled(it) },
                    )
                    Text(
                        text = stringResource(R.string.settings_media_encryption_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.mediaEncryptionEnabled)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            Color(0xFFFFB300),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }

            // Card 4 — Notifications (placeholder for plan 05)
            CategoryCard(title = stringResource(R.string.settings_cat_notifications)) {
                // TODO: Plan 05 adds Signal, Mattermost, rule config, and heartbeat sections here
                Text("—", style = MaterialTheme.typography.bodyMedium)
            }

            // Card 5 — App
            CategoryCard(title = stringResource(R.string.settings_cat_app)) {
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

                SettingsSection(title = stringResource(R.string.settings_log_level_title)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (logLevelDebug) stringResource(R.string.log_level_debug)
                                   else stringResource(R.string.log_level_normal),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = logLevelDebug,
                            onCheckedChange = { logLevelDebug = it },
                        )
                    }
                }

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

            Spacer(Modifier.height(16.dp))
        }
    }

    // PIN setup / change dialog
    if (showPinDialog) {
        PinSetupDialog(
            title = stringResource(
                if (pinDialogIsChange) R.string.settings_pin_change_title
                else R.string.settings_pin_setup_title
            ),
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                viewModel.setPin(pin)
                showPinDialog = false
            },
        )
    }
}

@Composable
private fun CategoryCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun PinSetupDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (pin: String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val tooShortMsg = stringResource(R.string.settings_pin_too_short)
    val mismatchMsg = stringResource(R.string.settings_pin_mismatch)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.all { ch -> ch.isDigit() } && it.length <= 6) {
                            pin = it
                            error = null
                        }
                    },
                    label = { Text(stringResource(R.string.settings_pin_enter)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        if (it.all { ch -> ch.isDigit() } && it.length <= 6) {
                            confirmPin = it
                            error = null
                        }
                    },
                    label = { Text(stringResource(R.string.settings_pin_confirm)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> error = tooShortMsg
                    pin != confirmPin -> error = mismatchMsg
                    else -> onConfirm(pin)
                }
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun AutoLockOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private val COUNTDOWN_OPTIONS = listOf(0, 15, 30, 60, 90, 120)
private val CALIBRATION_OPTIONS = listOf(5, 10, 20, 30)
private val CLIP_DURATION_OPTIONS = listOf(10, 30, 60)
private val LIGHT_SUPPRESS_OPTIONS = listOf(0, 10, 30, 60)
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
private fun clipDurationLabel(seconds: Int): String = when (seconds) {
    10 -> stringResource(R.string.clip_duration_10s)
    30 -> stringResource(R.string.clip_duration_30s)
    60 -> stringResource(R.string.clip_duration_60s)
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
private fun lightSuppressLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.light_suppress_off)
    10 -> stringResource(R.string.light_suppress_10s)
    30 -> stringResource(R.string.light_suppress_30s)
    60 -> stringResource(R.string.light_suppress_60s)
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
