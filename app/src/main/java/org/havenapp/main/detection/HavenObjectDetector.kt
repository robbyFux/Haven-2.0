package org.havenapp.main.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.havenapp.main.events.TriggerType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper um die MediaPipe Tasks Vision ObjectDetector API.
 *
 * Modell: EfficientDet Lite 0 (COCO, 80 Klassen, ~4 MB).
 * Das Modell wird beim ersten Aufruf von [initialize] geladen.
 * Ist die Datei nicht vorhanden, degradiert der Detector graceful:
 * [detect] gibt eine leere Liste zurück und [initError] enthält die Ursache.
 *
 * Migration von TFLite Task Vision 0.4.4 → MediaPipe Tasks Vision 0.10.29 (COMPAT-01):
 * Die öffentliche Schnittstelle ([initialize], [detect], [isAvailable], [availabilityFlow],
 * [initError]) bleibt unverändert — nur die interne Implementierung wechselt.
 *
 * COCO-Klassen → Haven TriggerType:
 *   "person"                                → CAMERA_PERSON
 *   "cat", "dog"                            → CAMERA_PET
 *   "car", "motorcycle", "bus", "truck",
 *   "bicycle", "train"                      → CAMERA_VEHICLE
 */
@Singleton
class HavenObjectDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLogger: org.havenapp.main.storage.AppLogger,
) {
    companion object {
        private const val TAG = "HavenObjectDetector"
        const val MODEL_FILENAME = "efficientdet_lite0.tflite"
        private const val SCORE_THRESHOLD = 0.45f
        private const val MAX_RESULTS = 5

        private val PET_LABELS = setOf("cat", "dog")
        private val VEHICLE_LABELS = setOf("car", "motorcycle", "bus", "truck", "bicycle", "train")
    }

    private var detector: ObjectDetector? = null
    private var initAttempted = false

    private val _isAvailable = MutableStateFlow(false)

    /** StateFlow, der sich ändert wenn das Modell geladen/entladen wird. */
    val availabilityFlow: StateFlow<Boolean> = _isAvailable.asStateFlow()

    /** True wenn das Modell geladen und einsatzbereit ist. */
    val isAvailable: Boolean get() = _isAvailable.value

    /** Null wenn erfolgreich geladen; sonst menschenlesbare Fehlerbeschreibung. */
    var initError: String? = null
        private set

    /** Lädt das MediaPipe ObjectDetector-Modell. Gibt true zurück wenn erfolgreich. */
    fun initialize(): Boolean {
        if (initAttempted) return detector != null
        initAttempted = true

        // Schritt 1: Datei in assets prüfen
        val fileExists = runCatching {
            context.assets.open(MODEL_FILENAME).close()
            true
        }.getOrDefault(false)

        if (!fileExists) {
            initError = "Model file not found in assets: $MODEL_FILENAME"
            Log.w(TAG, initError!!)
            appLogger.e(TAG, initError!!)
            return false
        }

        // Schritt 2: MediaPipe ObjectDetector laden (RunningMode.IMAGE für synchrone Inferenz)
        runCatching {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_FILENAME)
                .build()
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setMaxResults(MAX_RESULTS)
                .setScoreThreshold(SCORE_THRESHOLD)
                .build()
            detector = ObjectDetector.createFromOptions(context, options)
            _isAvailable.value = true
            Log.i(TAG, "MediaPipe ObjectDetector loaded successfully")
            appLogger.i(TAG, "MediaPipe model loaded: $MODEL_FILENAME (threshold=$SCORE_THRESHOLD, maxResults=$MAX_RESULTS)")
        }.onFailure {
            initError = generateSequence(it) { t -> t.cause?.takeIf { c -> c !== t } }
                .joinToString(" → ") { t -> "${t::class.simpleName}: ${t.message}" }
            Log.w(TAG, "MediaPipe model load failed (file exists): $initError")
            appLogger.e(TAG, "MediaPipe init failed: $initError")
        }
        return detector != null
    }

    /**
     * Führt Objekterkennung auf dem Bitmap aus.
     * @return Liste der erkannten [TriggerType]s passend zum [DetectionMode].
     *         Leer wenn kein Modell vorhanden oder keine relevanten Objekte erkannt.
     */
    fun detect(bitmap: Bitmap, mode: DetectionMode): List<TriggerType> {
        val d = detector ?: return emptyList()
        return runCatching {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = d.detect(mpImage)
            result.detections().flatMap { detection ->
                detection.categories().mapNotNull { cat ->
                    mapLabel(cat.categoryName().lowercase().trim(), mode)
                }
            }.distinct()
        }.getOrElse {
            val msg = "MediaPipe inference failed: ${it::class.simpleName}: ${it.message}"
            Log.e(TAG, msg, it)
            appLogger.e(TAG, msg)
            emptyList()
        }
    }

    private fun mapLabel(label: String, mode: DetectionMode): TriggerType? = when {
        label == "person" && mode.detectsPerson -> TriggerType.CAMERA_PERSON
        label in PET_LABELS && mode.detectsPet -> TriggerType.CAMERA_PET
        label in VEHICLE_LABELS && mode.detectsVehicle -> TriggerType.CAMERA_VEHICLE
        else -> null
    }

    fun close() {
        detector?.close()
        detector = null
        _isAvailable.value = false
        initAttempted = false
        initError = null
    }
}
