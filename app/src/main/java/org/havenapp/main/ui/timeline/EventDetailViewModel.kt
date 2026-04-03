package org.havenapp.main.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.havenapp.main.security.MediaEncryptionManager
import org.havenapp.main.storage.AppLogger
import org.havenapp.main.storage.EventRepository
import org.havenapp.main.storage.entity.EventTriggerEntity
import java.io.File
import javax.inject.Inject

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: EventRepository,
    private val appLogger: AppLogger,
) : ViewModel() {

    private companion object {
        const val TAG = "EventDetailViewModel"
    }

    private val eventId: Long = checkNotNull(savedStateHandle["eventId"])

    val triggers: StateFlow<List<EventTriggerEntity>> = repository
        .observeTriggersForEvent(eventId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Tracks which media paths the user has explicitly tapped for playback.
     * A path is in this set if the user tapped the thumbnail for that trigger.
     * Used to distinguish "not requested" (show placeholder) from "requested but pending"
     * (show spinner) and "resolved" (show player).
     */
    private val _requestedPaths = MutableStateFlow<Set<String>>(emptySet())
    val requestedPaths: StateFlow<Set<String>> = _requestedPaths

    /**
     * Per-trigger playback path cache.
     * Key: original mediaPath. Value: resolved path (decrypted temp file path or original path).
     * A path being in [requestedPaths] but NOT in this map means decryption is in progress.
     */
    private val _playbackPaths = MutableStateFlow<Map<String, String>>(emptyMap())
    val playbackPaths: StateFlow<Map<String, String>> = _playbackPaths

    /** All temp decrypted files created during this ViewModel's lifetime. */
    private val tempPlaybackFiles = mutableListOf<File>()

    /**
     * Called when the user taps a trigger's media thumbnail.
     *
     * Marks the path as requested (switches UI from placeholder to spinner) and schedules
     * async decryption of the .enc file on [Dispatchers.IO]. Once complete, [playbackPaths]
     * emits the resolved path and the UI switches to VideoPlayerCard.
     *
     * If [mediaPath] does not end with ".enc" the path is resolved immediately (no I/O).
     * Safe to call from the composition thread — never blocks.
     * No-op if already requested.
     */
    fun requestPlayback(mediaPath: String, cacheDir: File) {
        // Already requested — nothing to do (decryption may still be in progress).
        if (_requestedPaths.value.contains(mediaPath)) return

        _requestedPaths.value = _requestedPaths.value + mediaPath

        if (!mediaPath.endsWith(".enc")) {
            appLogger.i(TAG, "playback requested: plain file → $mediaPath")
            _playbackPaths.value = _playbackPaths.value + (mediaPath to mediaPath)
            return
        }

        appLogger.i(TAG, "playback requested: encrypted → $mediaPath — starting background decrypt")
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                decryptToTemp(mediaPath, cacheDir)
            }
            if (resolved == mediaPath) {
                appLogger.e(TAG, "playback fallback: decrypt did not produce a temp file, passing enc path to ExoPlayer")
            } else {
                appLogger.i(TAG, "playback ready: temp file → $resolved")
            }
            _playbackPaths.value = _playbackPaths.value + (mediaPath to resolved)
        }
    }

    /**
     * Decrypts [mediaPath] (.enc) to a temp file in [cacheDir].
     * Must be called on a background thread.
     * Returns the temp file path on success, or [mediaPath] as fallback on failure.
     */
    private fun decryptToTemp(mediaPath: String, cacheDir: File): String {
        val encFile = File(mediaPath)
        if (!encFile.exists()) {
            appLogger.e(TAG, "decryptToTemp: enc file not found: $mediaPath")
            return mediaPath
        }
        val tempFile = File(cacheDir, "playback_${System.currentTimeMillis()}.mp4")
        appLogger.d(TAG, "decryptToTemp: ${encFile.name} (${encFile.length()} bytes) → ${tempFile.name}")
        runCatching {
            MediaEncryptionManager.decryptToFile(encFile, tempFile)
        }.onFailure { e ->
            appLogger.e(TAG, "decryptToTemp failed: ${e.javaClass.simpleName}: ${e.message}")
            return mediaPath  // fallback: let ExoPlayer fail gracefully
        }
        synchronized(tempPlaybackFiles) { tempPlaybackFiles.add(tempFile) }
        return tempFile.absolutePath
    }

    override fun onCleared() {
        super.onCleared()
        synchronized(tempPlaybackFiles) {
            tempPlaybackFiles.forEach { it.delete() }
            tempPlaybackFiles.clear()
        }
    }

    fun deleteEvent(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
            onDeleted()
        }
    }
}
