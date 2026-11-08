package com.utkarsh.todo.feature.notes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.feature.notes.domain.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteUiState(val text: String = "", val isLoaded: Boolean = false, val saving: Boolean = false, val error: String? = null)

/** Edits today's scratchpad; typing is saved after a short pause rather than on every keystroke. */
@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NotesRepository,
    clock: Clock,
) : ViewModel() {

    private val day: LocalDate = LocalDate.now(clock)
    private val _state = MutableStateFlow(NoteUiState())
    val uiState: StateFlow<NoteUiState> = _state.asStateFlow()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            repository.note(day)
                .onSuccess { text -> _state.update { if (it.text.isEmpty()) it.copy(text = text, isLoaded = true) else it.copy(isLoaded = true) } }
                .onFailure { e -> _state.update { it.copy(isLoaded = true, error = e.message ?: "Couldn't load note") } }
        }
        // A note added from quick add changes the saved text; pick it up unless an edit is waiting to be saved.
        viewModelScope.launch {
            repository.changes.collect {
                if (saveJob?.isActive == true) return@collect
                repository.note(day).onSuccess { text -> _state.update { it.copy(text = text, isLoaded = true) } }
            }
        }
    }

    fun onTextChange(text: String) {
        _state.update { it.copy(text = text, error = null) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            _state.update { it.copy(saving = true) }
            repository.save(day, text)
                .onSuccess { _state.update { s -> s.copy(saving = false) } }
                .onFailure { _state.update { s -> s.copy(saving = false, error = "Couldn't save note") } }
        }
    }

    companion object {
        const val SAVE_DELAY_MS = 600L
    }
}
