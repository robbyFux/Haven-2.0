package org.havenapp.main.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.havenapp.main.MonitorService
import org.havenapp.main.R
import org.havenapp.main.detection.DetectionZone
import org.havenapp.main.sensor.MonitorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    onOpenZoneEditor: () -> Unit,
    viewModel: MonitorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val countdownSeconds by viewModel.countdownSeconds.collectAsStateWithLifecycle()
    val calibrationSecondsRemaining by viewModel.calibrationSecondsRemaining.collectAsStateWithLifecycle()
    val calibrationResults by viewModel.calibrationResults.collectAsStateWithLifecycle()
    val detectionZone by viewModel.detectionZone.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (state == MonitorState.IDLE) {
                        IconButton(onClick = onOpenZoneEditor) {
                            Icon(
                                imageVector = Icons.Filled.CropFree,
                                contentDescription = stringResource(R.string.settings_zone_title),
                                tint = if (detectionZone != null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (state) {
                    MonitorState.IDLE -> IdleContent(onStart = { viewModel.startMonitoring() })
                    MonitorState.COUNTDOWN -> CountdownContent(
                        seconds = countdownSeconds,
                        onCancel = { viewModel.stopMonitoring() },
                    )
                    MonitorState.CALIBRATING -> CalibratingContent(
                        secondsRemaining = calibrationSecondsRemaining,
                        results = calibrationResults,
                        detectionZone = detectionZone,
                        onCancel = { viewModel.stopMonitoring() },
                    )
                    MonitorState.ACTIVE -> ActiveContent(onStop = { viewModel.stopMonitoring() })
                }
            }
        }
    }
}

@Composable
private fun IdleContent(onStart: () -> Unit) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.monitor_status_idle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(
        onClick = onStart,
        modifier = Modifier.size(200.dp, 56.dp),
    ) {
        Text(
            text = stringResource(R.string.monitor_start),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CountdownContent(seconds: Int, onCancel: () -> Unit) {
    Text(
        text = stringResource(R.string.monitor_countdown_label),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "$seconds",
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(32.dp))
    OutlinedButton(onClick = onCancel) {
        Text(stringResource(R.string.monitor_cancel))
    }
}

@Composable
private fun CalibratingContent(
    secondsRemaining: Int,
    results: MonitorService.CalibrationResults?,
    detectionZone: DetectionZone?,
    onCancel: () -> Unit,
) {
    val calibrating = secondsRemaining > 0

    Text(
        text = stringResource(R.string.monitor_calibrating),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = if (calibrating) {
            stringResource(R.string.calibration_seconds_remaining, secondsRemaining)
        } else {
            stringResource(R.string.calibration_complete)
        },
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(32.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        CalibrationSensorRow(
            name = stringResource(R.string.calibration_sensor_motion),
            calibrating = calibrating,
            valueText = results?.motionNoiseFloor?.let {
                stringResource(R.string.calibration_noise_floor, "%.3f".format(it))
            },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        CalibrationSensorRow(
            name = stringResource(R.string.calibration_sensor_light),
            calibrating = calibrating,
            valueText = results?.lightEmaBaseline?.let {
                stringResource(R.string.calibration_noise_floor, "%.1f lux".format(it))
            },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        CalibrationSensorRow(
            name = stringResource(R.string.calibration_sensor_mic),
            calibrating = false,
            valueText = stringResource(R.string.calibration_fixed_threshold),
            fixedThreshold = true,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        CalibrationSensorRow(
            name = stringResource(R.string.calibration_sensor_camera),
            calibrating = false,
            valueText = if (detectionZone != null)
                stringResource(
                    R.string.calibration_camera_zone_active,
                    ((detectionZone.right - detectionZone.left) * 100).toInt(),
                    ((detectionZone.bottom - detectionZone.top) * 100).toInt(),
                )
            else
                stringResource(R.string.calibration_camera_full_frame),
            fixedThreshold = detectionZone == null,
        )
    }

    Spacer(modifier = Modifier.height(32.dp))
    OutlinedButton(onClick = onCancel) {
        Text(stringResource(R.string.monitor_cancel))
    }
}

@Composable
private fun CalibrationSensorRow(
    name: String,
    calibrating: Boolean,
    valueText: String?,
    fixedThreshold: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (fixedThreshold || !calibrating || valueText != null) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (fixedThreshold) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (valueText != null) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (!fixedThreshold) FontFamily.Monospace else FontFamily.Default,
                color = if (fixedThreshold) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun ActiveContent(onStop: () -> Unit) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.monitor_status_active),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(
        onClick = onStop,
        modifier = Modifier.size(200.dp, 56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Text(
            text = stringResource(R.string.monitor_stop),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
