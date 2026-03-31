package org.havenapp.main.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.havenapp.main.ui.theme.SeverityHigh
import org.havenapp.main.ui.theme.SeverityHighContainer
import org.havenapp.main.ui.theme.SeverityLow
import org.havenapp.main.ui.theme.SeverityLowContainer
import org.havenapp.main.ui.theme.SeverityMedium
import org.havenapp.main.ui.theme.SeverityMediumContainer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.havenapp.main.R
import org.havenapp.main.events.Severity
import org.havenapp.main.events.TriggerType
import org.havenapp.main.storage.entity.EventTriggerEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    val triggers by viewModel.triggers.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.deleteEvent(onDeleted = onBack) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete_event),
                        )
                    }
                },
            )
        }
    ) { padding ->
        if (triggers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.detail_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                items(triggers, key = { it.id }) { trigger ->
                    TriggerCard(
                        trigger = trigger,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TriggerCard(trigger: EventTriggerEntity, modifier: Modifier = Modifier) {
    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val type = TriggerType.fromId(trigger.type)
    val severity = Severity.entries.getOrNull(trigger.severity)

    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type?.label() ?: "Unbekannt (${trigger.type})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = timeFormatter.format(Date(trigger.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                trigger.sensorValue?.let { value ->
                    Text(
                        text = "%.2f".format(value),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            if (severity != null) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(severity.label(), style = MaterialTheme.typography.labelSmall) },
                    colors = severity.chipColors(),
                )
            }
        }
    }
}

private fun TriggerType.label(): String = when (this) {
    TriggerType.ACCELEROMETER -> "Beschleunigung"
    TriggerType.GYROSCOPE -> "Gyroskop"
    TriggerType.CAMERA -> "Kamera (Bewegung)"
    TriggerType.MICROPHONE -> "Mikrofon"
    TriggerType.PRESSURE -> "Luftdruck"
    TriggerType.LIGHT -> "Licht"
    TriggerType.POWER -> "Stromversorgung"
    TriggerType.BUMP -> "Erschütterung"
    TriggerType.CAMERA_VIDEO -> "Kamera (Video)"
    TriggerType.HEART -> "Heartbeat"
    TriggerType.CAMERA_PERSON -> "Person erkannt"
    TriggerType.CAMERA_PET -> "Tier erkannt"
    TriggerType.CAMERA_VEHICLE -> "Fahrzeug erkannt"
    TriggerType.CAMERA_LINGER -> "Person verweilt"
    TriggerType.CAMERA_ABSENT -> "Person weg"
    TriggerType.SOUND_DECIBEL -> "Lautstärke"
}

private fun Severity.label(): String = when (this) {
    Severity.LOW -> "Niedrig"
    Severity.MEDIUM -> "Mittel"
    Severity.HIGH -> "Hoch"
    Severity.CRITICAL -> "Kritisch"
}

@Composable
private fun Severity.chipColors() = when (this) {
    Severity.LOW -> SuggestionChipDefaults.suggestionChipColors(
        containerColor = SeverityLowContainer,
        labelColor = SeverityLow,
    )
    Severity.MEDIUM -> SuggestionChipDefaults.suggestionChipColors(
        containerColor = SeverityMediumContainer,
        labelColor = SeverityMedium,
    )
    Severity.HIGH -> SuggestionChipDefaults.suggestionChipColors(
        containerColor = SeverityHighContainer,
        labelColor = SeverityHigh,
    )
    Severity.CRITICAL -> SuggestionChipDefaults.suggestionChipColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        labelColor = MaterialTheme.colorScheme.error,
    )
}
