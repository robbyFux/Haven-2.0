package org.havenapp.main.ui.diagnostics

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.havenapp.main.BuildConfig
import org.havenapp.main.MonitorService
import org.havenapp.main.R
import org.havenapp.main.events.Severity
import org.havenapp.main.events.TriggerType
import org.havenapp.main.sensor.Sensitivity
import org.havenapp.main.storage.AppLogger
import org.havenapp.main.storage.entity.EventTriggerEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appLogs by viewModel.appLogs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_diagnostics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val text = buildShareText(uiState, appLogs)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_SUBJECT, "Haven Diagnostics")
                }
                context.startActivity(Intent.createChooser(intent, null))
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // ── Settings ──────────────────────────────────────────────────
            item {
                SectionHeader(stringResource(R.string.diag_settings_title))
                SettingsRow("Sensitivity", uiState.sensitivity.name)
                SettingsRow("  accel multiplier", "${uiState.sensitivity.accelerometerMultiplier}×")
                SettingsRow("  mic threshold", "${uiState.sensitivity.microphoneThresholdDb} dB")
                SettingsRow("  light delta", "${uiState.sensitivity.lightDeltaLux} lux")
                SettingsRow("  camera luma thr.", formatFraction(uiState.sensitivity.cameraMotionThreshold))
                SettingsRow("Camera", uiState.cameraPosition.name)
                SettingsRow("Countdown", "${uiState.countdownSeconds} s")
                SettingsRow("Calibration", "${uiState.calibrationSeconds} s")
                SettingsRow("Detection mode", uiState.detectionMode.name)
                SettingsRow(
                    "TFLite model",
                    if (uiState.tfliteAvailable) "loaded ✓" else "not available",
                    valueColor = if (uiState.tfliteAvailable) Color(0xFF4CAF50) else Color(0xFFFFB300),
                )
                uiState.tfliteInitError?.let { err ->
                    SettingsRow("  init error", err, valueColor = MaterialTheme.colorScheme.error)
                }
                SettingsRow(
                    "Detection zone",
                    uiState.detectionZone?.let { z ->
                        "%.0f%%–%.0f%% × %.0f%%–%.0f%%".format(
                            z.left * 100, z.right * 100, z.top * 100, z.bottom * 100,
                        )
                    } ?: "off (full frame)",
                )
                SettingsRow("App version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // ── Calibration strategy ──────────────────────────────────────
            item {
                SectionHeader(stringResource(R.string.diag_calibration_strategy_title))

                val calResults = uiState.calibrationResults
                val sens = uiState.sensitivity

                // Motion
                SubHeader("Motion (Fused Accel + Gyro)")
                SettingsRow("  algorithm", "Complementary filter α=0.7")
                SettingsRow("  noise floor (90th pct)", calResults?.motionNoiseFloor
                    ?.let { "%.5f".format(it) } ?: "— (run monitoring)")
                SettingsRow("  sensitivity multiplier", "${sens.accelerometerMultiplier}×")
                val motionThreshold = calResults?.motionNoiseFloor?.let { it * sens.accelerometerMultiplier }
                SettingsRow("  computed threshold", motionThreshold
                    ?.let { "%.5f".format(it) } ?: "— (pending calibration)")

                // Camera
                SubHeader("Camera (3-stage pipeline)")
                SettingsRow("  ROI zone", uiState.detectionZone?.let { z ->
                    "active  %.0f%%–%.0f%% × %.0f%%–%.0f%%  (%.0f%%×%.0f%%)".format(
                        z.left * 100, z.right * 100,
                        z.top * 100, z.bottom * 100,
                        (z.right - z.left) * 100,
                        (z.bottom - z.top) * 100,
                    )
                } ?: "off – full frame")
                SettingsRow("  stage 1  Luma-Diff thr.", formatFraction(sens.cameraMotionThreshold))
                SettingsRow("  stage 2  pHash Hamming", "≥ 4  (confirms structure change)")
                SettingsRow("  stage 3  TFLite score", "≥ 0.45  (EfficientDet Lite 0)")
                SettingsRow("  stage 3  min interval", "1.5 s  (OOM throttle)")
                SettingsRow("  stage 3  active", if (uiState.detectionMode.requiresML) "yes" else "no (MOTION_ONLY)")
                SettingsRow("  stage 3  model", if (uiState.tfliteAvailable) "loaded ✓" else "missing – fallback CAMERA")

                // Light
                SubHeader("Light (EMA baseline)")
                SettingsRow("  algorithm", "EMA α=0.02  (~10 s time const.)")
                SettingsRow("  EMA baseline", calResults?.lightEmaBaseline
                    ?.let { "%.1f lux".format(it) } ?: "— (run monitoring)")
                SettingsRow("  delta threshold", "${sens.lightDeltaLux} lux")
                SettingsRow("  cooldown after trigger", "30 s")

                // Microphone
                SubHeader("Microphone (absolute dB)")
                SettingsRow("  threshold", "${sens.microphoneThresholdDb} dB  (absolute, not relative)")
                SettingsRow("  cooldown", "1 s  (prevents burst-logging)")

                // Stop cool-down
                SubHeader("Stop cool-down")
                SettingsRow("  duration", "${MonitorService.STOP_COOLDOWN_SECONDS} s")
                SettingsRow("  reason", "walk-to-device; triggers discarded on stop")

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // ── Trigger log ───────────────────────────────────────────────
            item {
                SectionHeader(
                    stringResource(R.string.diag_log_title, uiState.recentTriggers.size)
                )
            }

            if (uiState.recentTriggers.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.diag_log_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(uiState.recentTriggers, key = { it.id }) { trigger ->
                    TriggerLogRow(trigger)
                }
            }

            // ── App error log ──────────────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.diag_app_log_title, appLogs.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.diag_app_log_clear),
                        )
                    }
                }
            }

            if (appLogs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.diag_app_log_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(appLogs) { entry ->
                    AppLogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SubHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AppLogRow(entry: AppLogger.Entry) {
    val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val levelTag = when (entry.level) {
        AppLogger.Level.DEBUG -> "D"
        AppLogger.Level.INFO  -> "I"
        AppLogger.Level.WARN  -> "W"
        AppLogger.Level.ERROR -> "E"
    }
    Text(
        text = "${fmt.format(Date(entry.timestamp))} $levelTag/${entry.tag}: ${entry.message}",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = when (entry.level) {
            AppLogger.Level.ERROR -> MaterialTheme.colorScheme.error
            AppLogger.Level.WARN  -> Color(0xFFFFB300)
            AppLogger.Level.INFO  -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            AppLogger.Level.DEBUG -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        },
        modifier = Modifier.padding(vertical = 1.dp),
    )
}

@Composable
private fun TriggerLogRow(trigger: EventTriggerEntity) {
    val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val type = TriggerType.fromId(trigger.type)?.name ?: "UNKNOWN(${trigger.type})"
    val severity = Severity.entries.getOrNull(trigger.severity)?.name ?: "?"
    val value = trigger.sensorValue?.let { "%.4f".format(it) } ?: "-"

    Text(
        text = "${fmt.format(Date(trigger.timestamp))}  %-18s  %-8s  %s".format(type, severity, value),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}

private fun formatFraction(f: Float): String =
    if (f == Float.MAX_VALUE) "off" else "%.2f".format(f)

private fun buildShareText(uiState: DiagnosticsUiState, appLogs: List<AppLogger.Entry>): String {
    val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val sens = uiState.sensitivity
    val cal = uiState.calibrationResults
    val sb = StringBuilder()

    sb.appendLine("Haven ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) – Diagnostics")
    sb.appendLine("Generated: ${dateFmt.format(Date())}")
    sb.appendLine()

    sb.appendLine("=== Settings ===")
    sb.appendLine("Sensitivity:           ${sens.name}")
    sb.appendLine("  accel multiplier     ${sens.accelerometerMultiplier}×")
    sb.appendLine("  mic threshold        ${sens.microphoneThresholdDb} dB")
    sb.appendLine("  light delta          ${sens.lightDeltaLux} lux")
    sb.appendLine("  camera luma thr.     ${formatFraction(sens.cameraMotionThreshold)}")
    sb.appendLine("Camera:                ${uiState.cameraPosition.name}")
    sb.appendLine("Countdown:             ${uiState.countdownSeconds} s")
    sb.appendLine("Calibration:           ${uiState.calibrationSeconds} s")
    sb.appendLine("Detection mode:        ${uiState.detectionMode.name}")
    sb.appendLine("TFLite model:          ${if (uiState.tfliteAvailable) "loaded" else "not available"}")
    uiState.tfliteInitError?.let { sb.appendLine("  init error:          $it") }
    sb.appendLine("Detection zone:        ${uiState.detectionZone?.let {
        "%.0f%%–%.0f%% × %.0f%%–%.0f%%".format(it.left*100, it.right*100, it.top*100, it.bottom*100)
    } ?: "off (full frame)"}")
    sb.appendLine()

    sb.appendLine("=== Calibration strategy ===")
    sb.appendLine("-- Motion (Fused Accel + Gyro) --")
    sb.appendLine("  algorithm:           Complementary filter α=0.7")
    sb.appendLine("  noise floor (90th):  ${cal?.motionNoiseFloor?.let { "%.5f".format(it) } ?: "—"}")
    sb.appendLine("  sens. multiplier:    ${sens.accelerometerMultiplier}×")
    val motionThr = cal?.motionNoiseFloor?.let { it * sens.accelerometerMultiplier }
    sb.appendLine("  computed threshold:  ${motionThr?.let { "%.5f".format(it) } ?: "—"}")
    sb.appendLine("-- Camera (3-stage) --")
    sb.appendLine("  ROI zone:            ${uiState.detectionZone?.let { z ->
        "active %.0f%%–%.0f%% × %.0f%%–%.0f%% (%.0f%%×%.0f%%)".format(
            z.left*100, z.right*100, z.top*100, z.bottom*100,
            (z.right-z.left)*100, (z.bottom-z.top)*100,
        )
    } ?: "off (full frame)"}")
    sb.appendLine("  stage 1 Luma-Diff:   ${formatFraction(sens.cameraMotionThreshold)}")
    sb.appendLine("  stage 2 pHash:       Hamming ≥ 4")
    sb.appendLine("  stage 3 TFLite:      score ≥ 0.45, active=${uiState.detectionMode.requiresML}, model=${if (uiState.tfliteAvailable) "loaded" else "missing"}")
    sb.appendLine("-- Light (EMA baseline) --")
    sb.appendLine("  algorithm:           EMA α=0.02 (~10 s)")
    sb.appendLine("  EMA baseline:        ${cal?.lightEmaBaseline?.let { "%.1f lux".format(it) } ?: "—"}")
    sb.appendLine("  delta threshold:     ${sens.lightDeltaLux} lux")
    sb.appendLine("  cooldown:            30 s")
    sb.appendLine("-- Microphone --")
    sb.appendLine("  threshold:           ${sens.microphoneThresholdDb} dB (absolute)")
    sb.appendLine("  cooldown:            1 s")
    sb.appendLine("-- Stop cool-down --")
    sb.appendLine("  duration:            ${MonitorService.STOP_COOLDOWN_SECONDS} s")
    sb.appendLine("  reason:              walk-to-device; triggers discarded retroactively on stop")
    sb.appendLine()

    sb.appendLine("=== Trigger Log (last ${uiState.recentTriggers.size}) ===")
    if (uiState.recentTriggers.isEmpty()) {
        sb.appendLine("(no triggers recorded)")
    } else {
        sb.appendLine("Time             Type               Severity   Value")
        sb.appendLine("-".repeat(60))
        uiState.recentTriggers.forEach { t ->
            val type = TriggerType.fromId(t.type)?.name ?: "UNKNOWN(${t.type})"
            val severity = Severity.entries.getOrNull(t.severity)?.name ?: "?"
            val value = t.sensorValue?.let { "%.4f".format(it) } ?: "-"
            sb.appendLine(
                "${timeFmt.format(Date(t.timestamp))}  %-18s %-10s %s"
                    .format(type, severity, value)
            )
        }
    }

    sb.appendLine()
    val logFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    sb.appendLine("=== App Log (${appLogs.size} entries) ===")
    if (appLogs.isEmpty()) {
        sb.appendLine("(no log entries)")
    } else {
        appLogs.forEach { e ->
            val lvl = when (e.level) {
                AppLogger.Level.DEBUG -> "D"
                AppLogger.Level.INFO  -> "I"
                AppLogger.Level.WARN  -> "W"
                AppLogger.Level.ERROR -> "E"
            }
            sb.appendLine("${logFmt.format(Date(e.timestamp))} $lvl/${e.tag}: ${e.message}")
        }
    }

    return sb.toString()
}
