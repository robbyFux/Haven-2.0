package org.havenapp.main.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.havenapp.main.detection.DetectionMode
import org.havenapp.main.detection.DetectionZone
import org.havenapp.main.detection.HavenObjectDetector
import org.havenapp.main.detection.PerceptualHashDetector
import org.havenapp.main.events.Severity
import org.havenapp.main.events.TriggerEvent
import org.havenapp.main.events.TriggerType
import org.havenapp.main.sensor.ExpertThresholds
import org.havenapp.main.sensor.Sensitivity
import org.havenapp.main.sensor.effectiveCameraFraction
import java.io.ByteArrayOutputStream

/**
 * Three-stage camera motion detection pipeline.
 *
 * Stage 1 — Luma diff (fast):
 *   WHY luma-first: fraction of changed pixels is cheap to compute directly from the
 *   Y-plane buffer; no bitmap allocation required. Gates the two slower stages so that
 *   ~80 % of frames are discarded here at near-zero CPU cost.
 *
 * Stage 2 — Perceptual hash (structural):
 *   WHY pHash gate: the 8×8 average hash reduces a frame to a 64-bit structural
 *   fingerprint. A uniform brightness change (light flicker, cloud shadow) shifts all
 *   pixel values uniformly but leaves the relative block structure unchanged → near-zero
 *   Hamming distance → no false trigger. Only real spatial motion (object moving through
 *   frame) flips blocks above/below the mean and produces a distance ≥ 4.
 *
 * Stage 3 — TFLite object detection (only when stages 1+2 confirm motion and ML is enabled):
 *   EfficientDet Lite 0 classifies person / pet / vehicle.
 *   WHY TFLite throttle ([TFLITE_MIN_INTERVAL_MS]): continuous per-frame inference would
 *   exhaust the device memory allocator on mid-range hardware (OOM). The 1 500 ms minimum
 *   interval caps inference at ~0.67 fps while still providing semantically-classified events.
 *   Stage gating (requiring stages 1+2 to pass) saves ~80 % of inference calls.
 *   Emits: CAMERA_PERSON / CAMERA_PET / CAMERA_VEHICLE or CAMERA as fallback.
 */
class CameraAnalyzer(
    private val sensitivity: Sensitivity,
    private val expert: ExpertThresholds = ExpertThresholds.DEFAULT,
    private val detectionMode: DetectionMode = DetectionMode.MOTION_ONLY,
    private val objectDetector: HavenObjectDetector? = null,
    private val zone: DetectionZone? = null,
) : ImageAnalysis.Analyzer {

    private val luminanceDetector = LuminanceMotionDetector()
    private val pHashDetector = PerceptualHashDetector()

    private val _events = Channel<TriggerEvent>(Channel.BUFFERED)
    val events: Flow<TriggerEvent> = _events.receiveAsFlow()

    private val motionThreshold: Float = sensitivity.effectiveCameraFraction(expert)
    private val hashThreshold = 4

    /** TFLite throttle: at most 1 inference per [TFLITE_MIN_INTERVAL_MS] ms to prevent OOM. */
    private var lastTfliteMs = 0L
    private val TFLITE_MIN_INTERVAL_MS = 1_500L

    /**
     * Last confirmed-motion frame as JPEG (quality 60, < 200 KB typical).
     * Updated on every frame that passes Stage 1+2 motion confirmation.
     * Read by NotificationRouter for CAMERA-type alert attachments (D-12).
     *
     * Known approximate behavior: the frame at notification send time may be
     * slightly newer than the triggering event (last frame wins). Acceptable for
     * alert thumbnails — exact frame synchronisation would complicate the pipeline.
     *
     * @Volatile: single writer (camera executor thread), multiple readers (notification coroutine).
     */
    @Volatile var lastJpegFrame: ByteArray? = null
        private set

    init {
        if (detectionMode.requiresML) {
            objectDetector?.initialize()
        }
    }

    override fun analyze(image: ImageProxy) {
        image.use {
            if (sensitivity == Sensitivity.OFF) return

            // Extract luma plane once — shared by both detectors to avoid double allocation
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride
            val width = image.width
            val height = image.height

            val raw = ByteArray(yBuffer.remaining())
            yBuffer.get(raw)

            val luma = ByteArray(width * height)
            for (row in 0 until height) {
                for (col in 0 until width) {
                    luma[row * width + col] = raw[row * rowStride + col * pixelStride]
                }
            }

            // Crop luma to the detection zone once — both detectors operate on the region
            // of interest without needing to know about zone coordinates themselves.
            val (effectiveLuma, effectiveW, effectiveH) = if (zone != null) {
                cropLuma(luma, width, height, zone)
            } else {
                Triple(luma, width, height)
            }

            // Stages 1 + 2: motion confirmation gate
            val lumaDiff = luminanceDetector.analyze(effectiveLuma, effectiveW, effectiveH) ?: return
            val hashDist = pHashDetector.analyze(effectiveLuma, effectiveW, effectiveH)

            val motionConfirmed = when {
                lumaDiff >= motionThreshold * 2f -> true
                hashDist == null -> lumaDiff >= motionThreshold
                else -> lumaDiff >= motionThreshold && hashDist >= hashThreshold
            }

            if (!motionConfirmed) return

            // Capture JPEG for notification attachment (D-12, < 200 KB at quality 60)
            runCatching {
                val uPlane = image.planes[1]
                val vPlane = image.planes[2]
                val uBuf = uPlane.buffer
                val vBuf = vPlane.buffer
                val nv21 = ByteArray(width * height * 3 / 2)
                luma.copyInto(nv21, destinationOffset = 0)
                var uvIdx = width * height
                for (row in 0 until height / 2) {
                    for (col in 0 until width / 2) {
                        nv21[uvIdx++] = vBuf.get(row * vPlane.rowStride + col * vPlane.pixelStride)
                        nv21[uvIdx++] = uBuf.get(row * uPlane.rowStride + col * uPlane.pixelStride)
                    }
                }
                val out = ByteArrayOutputStream()
                YuvImage(nv21, ImageFormat.NV21, width, height, null)
                    .compressToJpeg(Rect(0, 0, width, height), 60, out)
                lastJpegFrame = out.toByteArray()
            }

            // Stage 3: TFLite inference (only when mode requires ML, detector is available, and throttle allows)
            if (detectionMode.requiresML && objectDetector?.isAvailable == true) {
                val now = System.currentTimeMillis()
                if (now - lastTfliteMs < TFLITE_MIN_INTERVAL_MS) return  // throttle: not enough time elapsed
                lastTfliteMs = now
                val bitmap = buildBitmap(image, luma, width, height)
                if (bitmap != null) {
                    val detected = objectDetector.detect(bitmap, detectionMode)
                    if (detected.isNotEmpty()) {
                        val severity = deriveSeverity(lumaDiff)
                        detected.forEach { type ->
                            _events.trySend(
                                TriggerEvent(type = type, sensorValue = lumaDiff, severity = severity)
                            )
                        }
                        return
                    }
                    // ML available but no relevant objects detected — emit no event
                    return
                }
            }

            // Fallback: generic camera motion event (no ML or ML returned nothing)
            _events.trySend(
                TriggerEvent(
                    type = TriggerType.CAMERA,
                    sensorValue = lumaDiff,
                    severity = deriveSeverity(lumaDiff),
                )
            )
        }
    }

    private fun deriveSeverity(lumaDiff: Float): Severity = when {
        lumaDiff >= motionThreshold * 4f -> Severity.CRITICAL
        lumaDiff >= motionThreshold * 2f -> Severity.HIGH
        lumaDiff >= motionThreshold * 1.3f -> Severity.MEDIUM
        else -> Severity.LOW
    }

    /**
     * Crops the stride-normalized luma array to the detection zone.
     *
     * @return Triple of (croppedLuma, zoneWidth, zoneHeight) — densely packed, ready for both detectors.
     */
    private fun cropLuma(
        luma: ByteArray,
        width: Int,
        height: Int,
        zone: DetectionZone,
    ): Triple<ByteArray, Int, Int> {
        val b = zone.toPixelBounds(width, height)
        val cropped = ByteArray(b.zoneWidth * b.zoneHeight)
        for (row in 0 until b.zoneHeight) {
            val srcStart = (b.rowStart + row) * width + b.colStart
            luma.copyInto(cropped, row * b.zoneWidth, srcStart, srcStart + b.zoneWidth)
        }
        return Triple(cropped, b.zoneWidth, b.zoneHeight)
    }

    /**
     * Converts ImageProxy planes to an ARGB Bitmap for TFLite inference.
     * Uses the already-extracted Y data (luma) and reads the UV planes fresh.
     *
     * @return ARGB Bitmap, or null on conversion failure (graceful degradation).
     */
    private fun buildBitmap(image: ImageProxy, luma: ByteArray, width: Int, height: Int): Bitmap? {
        return runCatching {
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val uBuf = uPlane.buffer
            val vBuf = vPlane.buffer

            val nv21 = ByteArray(width * height * 3 / 2)
            // Y plane (already stride-normalized)
            luma.copyInto(nv21, destinationOffset = 0)

            // VU interleaved (NV21 format)
            var uvIdx = width * height
            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    nv21[uvIdx++] = vBuf.get(row * vPlane.rowStride + col * vPlane.pixelStride)
                    nv21[uvIdx++] = uBuf.get(row * uPlane.rowStride + col * uPlane.pixelStride)
                }
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 75, out)
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        }.getOrNull()
    }

    fun reset() {
        luminanceDetector.reset()
        pHashDetector.reset()
    }
}
