package org.havenapp.main.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Wraps CameraX VideoCapture<Recorder> to record timed video+audio clips.
 *
 * Enforces single-clip-at-a-time: [startClip] returns null if a recording
 * is already in progress. After the configured duration the clip is auto-stopped
 * and [onClipReady] fires with the output file path.
 *
 * If VideoCapture could not be bound to the camera (LEGACY hardware),
 * [isAvailable] is false and [startClip] always returns null.
 */
class ClipRecorder {

    companion object {
        private const val TAG = "HAVEN_VIDEO"
    }

    private var videoCapture: VideoCapture<Recorder>? = null
    private val activeRecording = AtomicReference<Recording?>(null)
    private var _isAvailable = false
    val isAvailable: Boolean get() = _isAvailable

    /** True while a clip is actively being recorded. Thread-safe (AtomicReference). */
    val isRecording: Boolean get() = activeRecording.get() != null

    /** Called by MonitorService after successful camera binding. */
    fun attach(videoCapture: VideoCapture<Recorder>) {
        this.videoCapture = videoCapture
        _isAvailable = true
        Log.d(TAG, "ClipRecorder.attach() — VideoCapture bound, recording available")
    }

    /** Called if VideoCapture binding failed (LEGACY hardware). */
    fun setUnavailable() {
        _isAvailable = false
        videoCapture = null
        Log.e(TAG, "ClipRecorder.setUnavailable() — VideoCapture NOT bound, recording disabled for this session")
    }

    /**
     * Starts a timed video+audio clip.
     *
     * @param context Android context for recording setup.
     * @param filesDir Directory where the clip file will be written.
     * @param durationSeconds Clip length in seconds (10, 30, or 60).
     * @param onClipReady Called on the recording executor thread when the clip is finalized.
     * @return output file path if recording started, or null if already recording or unavailable.
     */
    fun startClip(
        context: Context,
        filesDir: File,
        durationSeconds: Int,
        onClipReady: (outputPath: String) -> Unit,
    ): String? {
        if (!_isAvailable) {
            Log.e(TAG, "startClip() skipped — ClipRecorder is unavailable (VideoCapture not bound)")
            return null
        }
        if (activeRecording.get() != null) {
            Log.d(TAG, "startClip() skipped — already recording")
            return null
        }
        val vc = videoCapture
        if (vc == null) {
            Log.e(TAG, "startClip() skipped — videoCapture is null despite isAvailable=true (should not happen)")
            return null
        }

        val file = File(filesDir, "clip_${System.currentTimeMillis()}.mp4")
        Log.d(TAG, "startClip() — output file: ${file.absolutePath}")
        Log.d(TAG, "startClip() — filesDir exists=${filesDir.exists()} canWrite=${filesDir.canWrite()}")

        val opts = FileOutputOptions.Builder(file).build()

        val audioGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "startClip() — RECORD_AUDIO granted=$audioGranted, durationSeconds=$durationSeconds")

        val preparedRecording = vc.output.prepareRecording(context, opts).let {
            if (audioGranted) it.withAudioEnabled() else it
        }

        Log.d(TAG, "startClip() — calling preparedRecording.start()")
        val recording = preparedRecording
            .start(Executors.newSingleThreadExecutor()) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        Log.d(TAG, "VideoRecordEvent.Start — recording started for ${file.name}")
                    }
                    is VideoRecordEvent.Status -> {
                        // Periodic status events — not logged to avoid noise
                    }
                    is VideoRecordEvent.Pause -> {
                        Log.d(TAG, "VideoRecordEvent.Pause")
                    }
                    is VideoRecordEvent.Resume -> {
                        Log.d(TAG, "VideoRecordEvent.Resume")
                    }
                    is VideoRecordEvent.Finalize -> {
                        activeRecording.set(null)
                        val stats = event.recordingStats
                        val durationMs = stats.recordedDurationNanos / 1_000_000L
                        val bytes = stats.numBytesRecorded
                        if (event.hasError()) {
                            Log.e(
                                TAG,
                                "VideoRecordEvent.Finalize ERROR — errorCode=${event.error} " +
                                    "cause=${event.cause?.message} " +
                                    "durationMs=$durationMs bytes=$bytes " +
                                    "outputUri=${event.outputResults.outputUri} " +
                                    "file.exists=${file.exists()} file.length=${file.length()}",
                                event.cause,
                            )
                        } else {
                            Log.d(
                                TAG,
                                "VideoRecordEvent.Finalize OK — durationMs=$durationMs bytes=$bytes " +
                                    "outputUri=${event.outputResults.outputUri} " +
                                    "file.exists=${file.exists()} file.length=${file.length()}",
                            )
                            onClipReady(file.absolutePath)
                        }
                    }
                }
            }
        activeRecording.set(recording)
        Log.d(TAG, "startClip() — recording object set, auto-stop in ${durationSeconds}s")

        // Auto-stop after duration
        Handler(Looper.getMainLooper()).postDelayed(
            {
                Log.d(TAG, "startClip() auto-stop fired after ${durationSeconds}s")
                recording.stop()
            },
            durationSeconds * 1_000L,
        )
        return file.absolutePath
    }

    /** Stops any active recording immediately. */
    fun stopIfRecording() {
        val rec = activeRecording.getAndSet(null)
        if (rec != null) {
            Log.d(TAG, "stopIfRecording() — stopping active recording")
            rec.stop()
        }
    }
}
