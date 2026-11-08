package com.utkarsh.todo.feature.breakdown.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.feature.ai.domain.AiException
import com.utkarsh.todo.feature.ai.domain.AiRepository
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BreakdownPhase { LOADING, READY, ERROR, NOT_CONFIGURED }

data class Suggestion(val title: String, val selected: Boolean = true)

data class BreakdownUiState(
    val phase: BreakdownPhase = BreakdownPhase.LOADING,
    val suggestions: List<Suggestion> = emptyList(),
    /** Shown in the ERROR / NOT_CONFIGURED phases, and after a partly failed "Add". */
    val message: String? = null,
    val adding: Boolean = false,
) {
    val selectedCount: Int get() = suggestions.count { it.selected }
}

/**
 * Asks the assistant for subtasks of one task, lets the person untick the ones they don't want, and only then
 * adds them. Nothing is changed until [addSelected].
 */
@HiltViewModel
class BreakdownViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tasks: TasksRepository,
    private val ai: AiRepository,
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"]) { "taskId argument missing" }

    private val _state = MutableStateFlow(BreakdownUiState())
    val uiState: StateFlow<BreakdownUiState> = _state.asStateFlow()

    private val _added = Channel<Unit>(Channel.BUFFERED)
    /** Emits once every chosen step was added, so the sheet can close. */
    val added: Flow<Unit> = _added.receiveAsFlow()

    init {
        suggest()
    }

    fun suggest() {
        viewModelScope.launch {
            _state.value = BreakdownUiState(phase = BreakdownPhase.LOADING)
            val task = tasks.get(taskId).getOrElse { e ->
                return@launch fail(e.message ?: "Couldn't open this task")
            }
            ai.breakDown(task.title, task.notes, task.dueAt)
                .onSuccess { steps -> _state.value = BreakdownUiState(BreakdownPhase.READY, steps.map { Suggestion(it) }) }
                .onFailure { e -> fail(e.message ?: "Couldn't get suggestions", notConfigured = (e as? AiException)?.notConfigured == true) }
        }
    }

    fun toggle(index: Int) = _state.update { s ->
        if (s.adding || index !in s.suggestions.indices) s
        else s.copy(suggestions = s.suggestions.mapIndexed { i, it -> if (i == index) it.copy(selected = !it.selected) else it }, message = null)
    }

    fun addSelected() {
        val current = _state.value
        if (current.adding || current.phase != BreakdownPhase.READY || current.selectedCount == 0) return
        viewModelScope.launch {
            _state.update { it.copy(adding = true, message = null) }
            val chosen = _state.value.suggestions.filter { it.selected }
            var added = 0
            for (step in chosen) {
                if (tasks.addSubtask(taskId, step.title).isFailure) break
                added++
            }
            if (added == chosen.size) {
                _added.send(Unit)
            } else {
                // Keep only what still needs adding, so a retry never creates duplicates.
                val left = chosen.drop(added)
                _state.update {
                    it.copy(adding = false, suggestions = left, message = "Added $added of ${chosen.size}. Couldn't add the rest.")
                }
            }
        }
    }

    private fun fail(message: String, notConfigured: Boolean = false) {
        _state.value = BreakdownUiState(if (notConfigured) BreakdownPhase.NOT_CONFIGURED else BreakdownPhase.ERROR, message = message)
    }
}
