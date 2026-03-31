package org.havenapp.main.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.havenapp.main.storage.EventRepository
import org.havenapp.main.storage.entity.EventTriggerEntity
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

    fun deleteEvent(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
            onDeleted()
        }
    }
}
