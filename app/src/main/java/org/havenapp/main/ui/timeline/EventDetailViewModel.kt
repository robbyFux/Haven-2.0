package org.havenapp.main.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.havenapp.main.security.MediaEncryptionManager
import org.havenapp.main.storage.EventRepository
import org.havenapp.main.storage.entity.EventTriggerEntity
import java.io.File
import javax.inject.Inject

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: EventRepository,
) : ViewModel() {

    private val eventId: Long = checkNotNull(savedStateHandle["eventId"])

    val triggers: StateFlow<List<EventTriggerEntity>> = repository
        .observeTriggersForEvent(eventId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Holds the temp decrypted file so it can be deleted on ViewModel clear. */
    private var tempPlaybackFile: File? = null

    /**
     * Decrypts an encrypted media file (.enc) to a temp file in [cacheDir] for ExoPlayer playback.
     * Returns the temp file path, or the original path if not encrypted or if decryption fails.
     */
    fun preparePlaybackFile(mediaPath: String, cacheDir: File): String {
        if (!mediaPath.endsWith(".enc")) return mediaPath
        val encFile = File(mediaPath)
        if (!encFile.exists()) return mediaPath
        val tempFile = File(cacheDir, "playback_${System.currentTimeMillis()}.mp4")
        runCatching {
            MediaEncryptionManager.decryptToFile(encFile, tempFile)
        }.onFailure {
            return mediaPath  // fallback: let ExoPlayer fail gracefully
        }
        tempPlaybackFile = tempFile
        return tempFile.absolutePath
    }

    override fun onCleared() {
        super.onCleared()
        tempPlaybackFile?.delete()
        tempPlaybackFile = null
    }

    fun deleteEvent(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
            onDeleted()
        }
    }
}
