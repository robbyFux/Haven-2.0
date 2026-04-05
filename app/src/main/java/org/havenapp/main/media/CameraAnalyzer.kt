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
import org.havenapp.main.sensor.Sensitivity
import java.io.ByteArrayOutputStream

/**
 * Drei-Stufen Kamera-Bewegungserkennung:
 *
 *   Stufe 1 – Luminanz-Diff (schnell):
 *     Anteil veränderter Pixel → schnelles Gate für offensichtliche Bewegung.
 *
 *   Stufe 2 – Perceptual Hash (strukturell):
 *     Hamming-Distanz des 8×8 Average Hash → filtert Helligkeitsflicker heraus.
 *
 *   Stufe 3 – TFLite ObjectDetection (nur wenn Stufe 1+2 triggern und ML aktiviert):
 *     EfficientDet Lite 0 klassifiziert Person / Tier / Fahrzeug.
 *     Emit: CAMERA_PERSON / CAMERA_PET / CAMERA_VEHICLE oder CAMERA als Fallback.
 *
 * TFLite läuft nur bei bestätigter Bewegung → spart Akku.
 */
class CameraAnalyzer(
    private val sensitivity: Sensitivity,
    private val detectionMode: DetectionMode = DetectionMode.MOTION_ONLY,
    private val objectDetector: HavenObjectDetector? = null,
    private val zone: DetectionZone? = null,
) : ImageAnalysis.Analyzer {

    private val luminanceDetector = LuminanceMotionDetector()
    private val pHashDetector = PerceptualHashDetector()

    private val _events = Channel<TriggerEvent>(Channel.BUFFERED)
    val events: Flow<TriggerEvent> = _events.receiveAsFlow()

    private val motionThreshold: Float = sensitivity.cameraMotionThreshold
    private val hashThreshold = 4

    /** TFLite-Drosselung: max 1 Inferenz pro [TFLITE_MIN_INTERVAL_MS] ms, um OOM zu verhindern. */
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

            // Luma-Ebene einmalig extrahieren
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

            // Zone-Ausschnitt: Luma einmalig zuschneiden – beide Detektoren arbeiten dann
            // nur auf dem relevanten Bildbereich, ohne selbst von Zonen wissen zu müssen.
            val (effectiveLuma, effectiveW, effectiveH) = if (zone != null) {
                cropLuma(luma, width, height, zone)
            } else {
                Triple(luma, width, height)
            }

            // Stufe 1 + 2: Bewegungsbestätigung
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

            // Stufe 3: TFLite (nur wenn Modus ML verlangt, Detektor verfügbar und Drosselung erlaubt)
            if (detectionMode.requiresML && objectDetector?.isAvailable == true) {
                val now = System.currentTimeMillis()
                if (now - lastTfliteMs < TFLITE_MIN_INTERVAL_MS) return  // Drosselung: still warten
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
                    // ML verfügbar aber nichts Relevantes erkannt → kein Event
                    return
                }
            }

            // Fallback: generisches Kamera-Bewegungsevent
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
     * Schneidet das normalisierte Luma-Array auf die Zone zu.
     * Gibt ein neues dicht-gepacktes Array + die Zonenabmessungen zurück.
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
     * Konvertiert die ImageProxy-Ebenen in ein ARGB-Bitmap für TFLite.
     * Nutzt die bereits extrahierten Y-Daten (luma) + liest UV-Ebenen frisch.
     * Rückgabe null bei Konvertierungsfehler.
     */
    private fun buildBitmap(image: ImageProxy, luma: ByteArray, width: Int, height: Int): Bitmap? {
        return runCatching {
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val uBuf = uPlane.buffer
            val vBuf = vPlane.buffer

            val nv21 = ByteArray(width * height * 3 / 2)
            // Y-Ebene (bereits stride-normalisiert)
            luma.copyInto(nv21, destinationOffset = 0)

            // VU interleaved (NV21-Format)
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
