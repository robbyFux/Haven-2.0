package org.havenapp.main.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.havenapp.main.storage.EventRepository
import org.havenapp.main.storage.entity.EventEntity
import javax.inject.Inject

data class TimelineUiState(
    val events: List<EventEntity> = emptyList(),
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: EventRepository,
) : ViewModel() {

    val uiState: StateFlow<TimelineUiState> = repository.observeRecentEvents()
        .map { TimelineUiState(events = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch { repository.deleteEvent(eventId) }
    }
}
