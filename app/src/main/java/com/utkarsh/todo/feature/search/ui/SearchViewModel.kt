package com.utkarsh.todo.feature.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.core.common.formatRelativeDue
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
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

data class SearchResultUi(
    val id: String,
    val title: String,
    val done: Boolean,
    val dueLabel: String?,
    val projectName: String?,
    val projectColorHex: String?,
)

enum class SearchPhase { IDLE, LOADING, RESULTS, EMPTY, ERROR }

data class SearchUiState(
    val query: String = "",
    val phase: SearchPhase = SearchPhase.IDLE,
    val results: List<SearchResultUi> = emptyList(),
    val error: String? = null,
)

/** Finds tasks by title as you type. Waits a moment after the last key so it does not ask the server for every letter. */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val tasks: TasksRepository,
    private val projects: ProjectsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var projectsById: Map<String, Project> = emptyMap()

    init {
        viewModelScope.launch { projects.projects().onSuccess { list -> projectsById = list.associateBy { it.id } } }
        // A task changed somewhere else (for example on the task page): refresh what is showing.
        viewModelScope.launch { tasks.changes.collect { if (_state.value.query.isNotBlank()) run(_state.value.query.trim(), quiet = true) } }
    }

    fun onQueryChange(text: String) {
        searchJob?.cancel()
        val query = text.take(MAX_QUERY)
        if (query.isBlank()) {
            _state.value = SearchUiState(query = query)
            return
        }
        _state.update { it.copy(query = query, phase = SearchPhase.LOADING, error = null) }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            run(query.trim(), quiet = false)
        }
    }

    fun retry() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        _state.update { it.copy(phase = SearchPhase.LOADING, error = null) }
        searchJob = viewModelScope.launch { run(query, quiet = false) }
    }

    fun clear() = onQueryChange("")

    private suspend fun run(query: String, quiet: Boolean) {
        val result = tasks.search(query)
        // The user kept typing while we waited: that answer belongs to an older question.
        if (_state.value.query.trim() != query) return
        result
            .onSuccess { found ->
                val rows = found.sortedBy { it.isDone }.map { it.toUi() }
                _state.update { it.copy(results = rows, phase = if (rows.isEmpty()) SearchPhase.EMPTY else SearchPhase.RESULTS, error = null) }
            }
            .onFailure { failure ->
                if (quiet) return
                _state.update { it.copy(results = emptyList(), phase = SearchPhase.ERROR, error = failure.message ?: "Couldn't search") }
            }
    }

    private fun Task.toUi(): SearchResultUi {
        val project = projectId?.let(projectsById::get)
        val today: LocalDate = LocalDate.now(clock)
        return SearchResultUi(id, title, isDone, dueAt?.formatRelativeDue(clock.zone, today), project?.name, project?.colorHex)
    }

    companion object {
        const val DEBOUNCE_MS = 300L
        const val MAX_QUERY = 80
    }
}
