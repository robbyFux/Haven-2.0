package org.havenapp.main.ui.timeline

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
import java.io.File
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
    val requestedPaths by viewModel.requestedPaths.collectAsStateWithLifecycle()
    val playbackPaths by viewModel.playbackPaths.collectAsStateWithLifecycle()

    // Filter state: null = show all, non-null = show only that type (D-08, D-10)
    var selectedTriggerType by remember { mutableStateOf<TriggerType?>(null) }

    // Derive available types from the trigger list (D-08: dynamic, not hardcoded)
    val availableTypes = remember(triggers) {
        triggers.mapNotNull { TriggerType.fromId(it.type) }.distinct().sortedBy { it.ordinal }
    }

    // Filtered trigger list via derivedStateOf (per D-08: derivedStateOf computation)
    val filteredTriggers by remember {
        derivedStateOf {
            if (selectedTriggerType == null) triggers
            else triggers.filter { TriggerType.fromId(it.type) == selectedTriggerType }
        }
    }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // FilterChip row (D-08)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = selectedTriggerType == null,
                            onClick = { selectedTriggerType = null },
                            label = { Text(stringResource(R.string.filter_all)) },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                    items(availableTypes.size) { index ->
                        val type = availableTypes[index]
                        FilterChip(
                            selected = selectedTriggerType == type,
                            onClick = {
                                selectedTriggerType = if (selectedTriggerType == type) null else type
                            },
                            label = { Text(type.label()) },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }

                // Filtered trigger list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                ) {
                    items(filteredTriggers, key = { it.id }) { trigger ->
                        TriggerCard(
                            trigger = trigger,
                            viewModel = viewModel,
                            requestedPaths = requestedPaths,
                            playbackPaths = playbackPaths,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TriggerCard(
    trigger: EventTriggerEntity,
    viewModel: EventDetailViewModel,
    requestedPaths: Set<String>,
    playbackPaths: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val type = TriggerType.fromId(trigger.type)
    val severity = Severity.entries.getOrNull(trigger.severity)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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

            trigger.mediaPath?.let { path ->
                Spacer(Modifier.height(8.dp))
                val isRequested = requestedPaths.contains(path)
                val resolvedPath = playbackPaths[path]

                when {
                    // Not yet requested: show tappable placeholder thumbnail
                    !isRequested -> {
                        MediaThumbnailPlaceholder(
                            onClick = { viewModel.requestPlayback(path, context.cacheDir) },
                        )
                    }
                    // Requested but decryption still in progress: show spinner
                    resolvedPath == null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    // Decryption complete: show player
                    else -> {
                        VideoPlayerCard(resolvedPath = resolvedPath)
                    }
                }
            }
        }
    }
}

/**
 * Static placeholder shown in the trigger list before the user taps to play.
 * Tapping calls [onClick] which schedules decryption and switches to the spinner state.
 */
@Composable
private fun MediaThumbnailPlaceholder(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = stringResource(R.string.detail_play_video),
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.detail_play_video),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerCard(resolvedPath: String) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isFullscreen = remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(resolvedPath) {
        val uri = Uri.fromFile(File(resolvedPath))
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            exoPlayer.release()
        }
    }

    // Lock/restore orientation with fullscreen state.
    LaunchedEffect(isFullscreen.value) {
        activity?.requestedOrientation = if (isFullscreen.value)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // Back button exits fullscreen instead of navigating away.
    BackHandler(enabled = isFullscreen.value) {
        isFullscreen.value = false
    }

    // Inline (normal) player — hidden while fullscreen dialog is open so the
    // same ExoPlayer instance can be reattached to the dialog's PlayerView.
    if (!isFullscreen.value) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setFullscreenButtonClickListener { isFullscreen.value = true }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )
    }

    // Fullscreen dialog — renders the same ExoPlayer instance edge-to-edge.
    if (isFullscreen.value) {
        Dialog(
            onDismissRequest = { isFullscreen.value = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            setFullscreenButtonClickListener { isFullscreen.value = false }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
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
