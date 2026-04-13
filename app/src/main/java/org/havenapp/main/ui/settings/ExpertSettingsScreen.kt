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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.havenapp.main.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertSettingsScreen(
    onBack: () -> Unit,
    viewModel: ExpertSettingsViewModel = hiltViewModel(),
) {
    val expert by viewModel.expertThresholds.collectAsStateWithLifecycle()

    // Per-sensor slider state: re-initialises from flow emission when an external reset occurs.
    var accelSlider by remember(expert.accelMediumMultiplier) {
        mutableFloatStateOf(expert.accelMediumMultiplier ?: 2.0f)
    }
    var micSlider by remember(expert.micMediumDb) {
        mutableFloatStateOf(expert.micMediumDb ?: 55f)
    }
    var lightSlider by remember(expert.lightMediumLux) {
        mutableFloatStateOf(expert.lightMediumLux ?: 40f)
    }
    var cameraSlider by remember(expert.cameraMediumFraction) {
        mutableFloatStateOf(expert.cameraMediumFraction ?: 0.08f)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.expert_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.expert_settings_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(0.dp))

            // ── Accelerometer ────────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.expert_accelerometer), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Medium: ${"%.2f".format(accelSlider)}\u00d7",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Slider(
                        value = accelSlider,
                        onValueChange = { accelSlider = it },
                        onValueChangeFinished = { viewModel.setAccelMedium(accelSlider) },
                        valueRange = 0.5f..8.0f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Low: ${"%.2f".format((accelSlider + 2.0f).coerceAtLeast(0.5f))}\u00d7",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "High: ${"%.2f".format((accelSlider - 1.0f).coerceAtLeast(0.5f))}\u00d7",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    TextButton(onClick = { viewModel.setAccelMedium(null) }) {
                        Text(stringResource(R.string.expert_reset_one))
                    }
                }
            }

            // ── Microphone ───────────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.expert_microphone), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Medium: ${"%.0f".format(micSlider)} dB",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Slider(
                        value = micSlider,
                        onValueChange = { micSlider = it },
                        onValueChangeFinished = { viewModel.setMicMedium(micSlider) },
                        valueRange = 30f..80f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Low: ${"%.0f".format((micSlider + 10f).coerceAtLeast(20f))} dB",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "High: ${"%.0f".format((micSlider - 10f).coerceAtLeast(20f))} dB",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    TextButton(onClick = { viewModel.setMicMedium(null) }) {
                        Text(stringResource(R.string.expert_reset_one))
                    }
                }
            }

            // ── Light sensor ─────────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.expert_light), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Medium: ${"%.0f".format(lightSlider)} lux",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Slider(
                        value = lightSlider,
                        onValueChange = { lightSlider = it },
                        onValueChangeFinished = { viewModel.setLightMedium(lightSlider) },
                        valueRange = 5f..150f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Low: ${"%.0f".format((lightSlider + 40f).coerceAtLeast(5f))} lux",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "High: ${"%.0f".format((lightSlider - 20f).coerceAtLeast(5f))} lux",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    TextButton(onClick = { viewModel.setLightMedium(null) }) {
                        Text(stringResource(R.string.expert_reset_one))
                    }
                }
            }

            // ── Camera motion ────────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.expert_camera_motion), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Medium: ${"%.2f".format(cameraSlider)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Slider(
                        value = cameraSlider,
                        onValueChange = { cameraSlider = it },
                        onValueChangeFinished = { viewModel.setCameraMedium(cameraSlider) },
                        valueRange = 0.01f..0.30f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Low: ${"%.2f".format((cameraSlider + 0.10f).coerceAtLeast(0.01f))}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "High: ${"%.2f".format((cameraSlider - 0.05f).coerceAtLeast(0.01f))}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    TextButton(onClick = { viewModel.setCameraMedium(null) }) {
                        Text(stringResource(R.string.expert_reset_one))
                    }
                }
            }

            // ── Global reset ─────────────────────────────────────────────────
            OutlinedButton(
                onClick = { viewModel.resetAll() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.expert_reset_all))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
