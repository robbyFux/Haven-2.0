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
 * Wrapper around the MediaPipe Tasks Vision ObjectDetector API.
 *
 * Model: EfficientDet Lite 0 (COCO, 80 classes, ~4 MB).
 * WHY EfficientDet Lite 0: ~4 MB model size fits within the 16 KB page-size
 * alignment requirement on Android 16+; the smallest EfficientDet variant still
 * achieves acceptable accuracy for security-relevant COCO categories (person,
 * pets, vehicles) at ~4 ms/frame on mid-range hardware.
 *
 * The model is loaded on the first call to [initialize].
 * If the asset file is absent, the detector degrades gracefully:
 * [detect] returns an empty list and [initError] holds the cause.
 *
 * WHY RunningMode.IMAGE: synchronous per-frame inference avoids callback threading
 * on the ImageAnalysis executor. Each analyze() call blocks for the duration of
 * inference; the 1 500 ms throttle in CameraAnalyzer prevents OOM from back-pressure.
 *
 * Migration from TFLite Task Vision 0.4.4 → MediaPipe Tasks Vision 0.10.29 (COMPAT-01):
 * The public interface ([initialize], [detect], [isAvailable], [availabilityFlow],
 * [initError]) is unchanged — only the internal implementation changes.
 *
 * COCO class → Haven TriggerType mapping:
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

    /** StateFlow that changes when the model is loaded or unloaded. */
    val availabilityFlow: StateFlow<Boolean> = _isAvailable.asStateFlow()

    /** True when the model is loaded and ready for inference. */
    val isAvailable: Boolean get() = _isAvailable.value

    /** Null when successfully loaded; otherwise a human-readable error description. */
    var initError: String? = null
        private set

    /**
     * Loads the MediaPipe ObjectDetector model from assets.
     *
     * @return True if the model loaded successfully; false otherwise (see [initError] for cause).
     */
    fun initialize(): Boolean {
        if (initAttempted) return detector != null
        initAttempted = true

        // Step 1: verify asset file exists before attempting model load
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

        // Step 2: build and load the MediaPipe ObjectDetector (RunningMode.IMAGE for synchronous inference)
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
     * Runs object detection on the given bitmap.
     *
     * WHY graceful degradation: if the model file is absent or failed to load,
     * this returns an empty list instead of throwing. The app continues with
     * MOTION_ONLY detection rather than crashing.
     *
     * @param bitmap Frame to analyse; should be the full-resolution camera frame.
     * @param mode Detection mode controlling which COCO classes produce trigger events.
     * @return List of detected [TriggerType]s matching the active [DetectionMode].
     *   Empty when no model is loaded or no relevant objects were detected.
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
