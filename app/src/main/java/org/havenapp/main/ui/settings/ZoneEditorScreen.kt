package org.havenapp.main.ui.settings

import android.content.res.Configuration
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.havenapp.main.R
import org.havenapp.main.detection.DetectionZone
import org.havenapp.main.sensor.CameraPosition
import kotlin.math.abs
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneEditorScreen(
    onBack: () -> Unit,
    viewModel: ZoneEditorViewModel = hiltViewModel(),
) {
    val savedZone by viewModel.zone.collectAsStateWithLifecycle()
    val cameraPosition by viewModel.cameraPosition.collectAsStateWithLifecycle()

    var draftZone by remember(savedZone) { mutableStateOf(savedZone) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    val primary = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    val cameraSelector = when (cameraPosition) {
        CameraPosition.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        CameraPosition.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
    }

    DisposableEffect(cameraSelector) {
        var cameraProvider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            runCatching {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { cameraProvider?.unbindAll() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.zone_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (isLandscape) {
            // Landscape: camera on the left, controls on the right
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                ZoneCameraBox(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(4f / 3f, matchHeightConstraintsFirst = true),
                    previewView = previewView,
                    draftZone = draftZone,
                    dragStart = dragStart,
                    dragCurrent = dragCurrent,
                    primary = primary,
                    onDragStart = { dragStart = it; dragCurrent = it },
                    onDrag = { dragCurrent = it },
                    onDragEnd = { start, end, w, h ->
                        draftZone = buildZone(start, end, w, h)
                        dragStart = null; dragCurrent = null
                    },
                    onDragCancel = { dragStart = null; dragCurrent = null },
                )
                Spacer(Modifier.width(16.dp))
                ZoneEditorControls(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp),
                    draftZone = draftZone,
                    onClear = { viewModel.clearZoneAndBack(onBack) },
                    onSave = { draftZone?.let { viewModel.saveZoneAndBack(it, onBack) } },
                )
            }
        } else {
            // Portrait: camera on top, controls below
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                ZoneCameraBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                    previewView = previewView,
                    draftZone = draftZone,
                    dragStart = dragStart,
                    dragCurrent = dragCurrent,
                    primary = primary,
                    onDragStart = { dragStart = it; dragCurrent = it },
                    onDrag = { dragCurrent = it },
                    onDragEnd = { start, end, w, h ->
                        draftZone = buildZone(start, end, w, h)
                        dragStart = null; dragCurrent = null
                    },
                    onDragCancel = { dragStart = null; dragCurrent = null },
                )
                Spacer(Modifier.height(12.dp))
                ZoneEditorControls(
                    draftZone = draftZone,
                    onClear = { viewModel.clearZoneAndBack(onBack) },
                    onSave = { draftZone?.let { viewModel.saveZoneAndBack(it, onBack) } },
                )
            }
        }
    }
}

/** Builds a normalized DetectionZone from drag coordinates; null if the selection is too small. */
private fun buildZone(start: Offset, end: Offset, canvasW: Float, canvasH: Float): DetectionZone? {
    if (canvasW <= 0f || canvasH <= 0f) return null
    val minX = min(start.x, end.x).coerceIn(0f, canvasW)
    val maxX = kotlin.math.max(start.x, end.x).coerceIn(0f, canvasW)
    val minY = min(start.y, end.y).coerceIn(0f, canvasH)
    val maxY = kotlin.math.max(start.y, end.y).coerceIn(0f, canvasH)
    if (abs(maxX - minX) / canvasW <= 0.05f || abs(maxY - minY) / canvasH <= 0.05f) return null
    return DetectionZone(minX / canvasW, minY / canvasH, maxX / canvasW, maxY / canvasH)
}

@Composable
private fun ZoneCameraBox(
    modifier: Modifier,
    previewView: PreviewView,
    draftZone: DetectionZone?,
    dragStart: Offset?,
    dragCurrent: Offset?,
    primary: Color,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (start: Offset, end: Offset, w: Float, h: Float) -> Unit,
    onDragCancel: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                var localStart: Offset? = null
                var localCurrent: Offset? = null
                detectDragGestures(
                    onDragStart = {
                        localStart = it
                        localCurrent = it
                        onDragStart(it)
                    },
                    onDrag = { change, _ ->
                        localCurrent = change.position
                        onDrag(change.position)
                    },
                    onDragEnd = {
                        val start = localStart
                        val end = localCurrent
                        if (start != null && end != null) {
                            onDragEnd(start, end, size.width.toFloat(), size.height.toFloat())
                        }
                        localStart = null
                        localCurrent = null
                    },
                    onDragCancel = {
                        localStart = null
                        localCurrent = null
                        onDragCancel()
                    },
                )
            },
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Rule-of-thirds grid overlay
            for (i in 1..2) {
                drawLine(Color.White.copy(alpha = 0.25f), Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), strokeWidth = 0.8f)
                drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, h * i / 3f), Offset(w, h * i / 3f), strokeWidth = 0.8f)
            }

            val ds = dragStart
            val dc = dragCurrent
            if (ds != null && dc != null) {
                val rect = Rect(
                    left = min(ds.x, dc.x),
                    top = min(ds.y, dc.y),
                    right = kotlin.math.max(ds.x, dc.x),
                    bottom = kotlin.math.max(ds.y, dc.y),
                )
                drawOutsideDimOverlay(rect, w, h)
                drawRect(
                    color = primary,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))),
                )
            } else {
                val z = draftZone
                if (z != null) {
                    val zRect = Rect(z.left * w, z.top * h, z.right * w, z.bottom * h)
                    drawOutsideDimOverlay(zRect, w, h)
                    drawRect(
                        color = primary,
                        topLeft = Offset(zRect.left, zRect.top),
                        size = Size(zRect.width, zRect.height),
                        style = Stroke(width = 2.5f),
                    )
                    listOf(
                        Offset(zRect.left, zRect.top), Offset(zRect.right, zRect.top),
                        Offset(zRect.left, zRect.bottom), Offset(zRect.right, zRect.bottom),
                    ).forEach { corner ->
                        drawCircle(Color.White, 5f, corner)
                        drawCircle(primary, 3.5f, corner)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneEditorControls(
    modifier: Modifier = Modifier,
    draftZone: DetectionZone?,
    onClear: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.zone_editor_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (draftZone != null) {
            Text(
                text = stringResource(
                    R.string.zone_editor_info,
                    (draftZone.left * 100).toInt(),
                    (draftZone.top * 100).toInt(),
                    (draftZone.right * 100).toInt(),
                    (draftZone.bottom * 100).toInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.zone_editor_no_zone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.zone_editor_clear))
            }
            Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = draftZone != null) {
                Text(stringResource(R.string.zone_editor_save))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOutsideDimOverlay(
    zone: Rect,
    w: Float,
    h: Float,
) {
    val dim = Color.Black.copy(alpha = 0.45f)
    if (zone.top > 0f) drawRect(dim, Offset.Zero, Size(w, zone.top))
    if (zone.bottom < h) drawRect(dim, Offset(0f, zone.bottom), Size(w, h - zone.bottom))
    if (zone.left > 0f) drawRect(dim, Offset(0f, zone.top), Size(zone.left, zone.height))
    if (zone.right < w) drawRect(dim, Offset(zone.right, zone.top), Size(w - zone.right, zone.height))
}
