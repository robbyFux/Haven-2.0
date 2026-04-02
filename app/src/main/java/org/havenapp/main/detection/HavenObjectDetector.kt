package org.havenapp.main.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.havenapp.main.events.TriggerType
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper um die TFLite Task Library ObjectDetector API.
 *
 * Modell: EfficientDet Lite 0 (COCO, 80 Klassen, ~4 MB).
 * Das Modell wird beim ersten Aufruf von [initialize] geladen.
 * Ist die Datei nicht vorhanden, degradiert der Detector graceful:
 * [detect] gibt eine leere Liste zurück und [initError] enthält die Ursache.
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

    /** Lädt das TFLite-Modell. Gibt true zurück wenn erfolgreich. */
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

        // Schritt 2: Modell laden
        runCatching {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(MAX_RESULTS)
                .setScoreThreshold(SCORE_THRESHOLD)
                .build()
            detector = ObjectDetector.createFromFileAndOptions(context, MODEL_FILENAME, options)
            _isAvailable.value = true
            Log.i(TAG, "TFLite model loaded successfully")
            appLogger.i(TAG, "TFLite model loaded: $MODEL_FILENAME (threshold=$SCORE_THRESHOLD, maxResults=$MAX_RESULTS)")
        }.onFailure {
            initError = "${it::class.simpleName}: ${it.message}"
            Log.w(TAG, "TFLite model load failed (file exists): $initError")
            appLogger.e(TAG, "TFLite init failed: $initError")
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
            val tensorImage = TensorImage.fromBitmap(bitmap)
            d.detect(tensorImage)
                .flatMap { detection ->
                    detection.categories.mapNotNull { cat ->
                        mapLabel(cat.label.lowercase().trim(), mode)
                    }
                }
                .distinct()
        }.getOrElse {
            val msg = "TFLite inference failed: ${it::class.simpleName}: ${it.message}"
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
