package org.havenapp.main.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
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

    private var videoCapture: VideoCapture<Recorder>? = null
    private val activeRecording = AtomicReference<Recording?>(null)
    private var _isAvailable = false
    val isAvailable: Boolean get() = _isAvailable

    /** Called by MonitorService after successful camera binding. */
    fun attach(videoCapture: VideoCapture<Recorder>) {
        this.videoCapture = videoCapture
        _isAvailable = true
    }

    /** Called if VideoCapture binding failed (LEGACY hardware). */
    fun setUnavailable() {
        _isAvailable = false
        videoCapture = null
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
        if (!_isAvailable || activeRecording.get() != null) return null
        val vc = videoCapture ?: return null

        val file = File(filesDir, "clip_${System.currentTimeMillis()}.mp4")
        val opts = FileOutputOptions.Builder(file).build()

        val recording = vc.output
            .prepareRecording(context, opts)
            .withAudioEnabled()
            .start(Executors.newSingleThreadExecutor()) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording.set(null)
                    if (!event.hasError()) {
                        onClipReady(file.absolutePath)
                    }
                }
            }
        activeRecording.set(recording)

        // Auto-stop after duration
        Handler(Looper.getMainLooper()).postDelayed(
            { recording.stop() },
            durationSeconds * 1_000L,
        )
        return file.absolutePath
    }

    /** Stops any active recording immediately. */
    fun stopIfRecording() {
        activeRecording.getAndSet(null)?.stop()
    }
}
