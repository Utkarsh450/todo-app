package com.utkarsh.todo.feature.quickadd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.quickadd.domain.QuickAddParser
import com.utkarsh.todo.feature.quickadd.domain.QuickAddResult
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickAddUiState(
    val text: String = "",
    val parsed: QuickAddResult? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = !isSaving && !parsed?.title.isNullOrBlank()
}

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val tasks: TasksRepository,
    private val projects: ProjectsRepository,
    private val parser: QuickAddParser,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickAddUiState())
    val uiState: StateFlow<QuickAddUiState> = _state.asStateFlow()

    private val _added = Channel<Unit>(Channel.BUFFERED)
    /** Emits once a task was created, so the sheet can close. */
    val added: Flow<Unit> = _added.receiveAsFlow()

    private var knownProjects: List<Project> = emptyList()

    init {
        viewModelScope.launch { knownProjects = projects.projects().getOrDefault(emptyList()) }
    }

    fun onTextChange(text: String) = _state.update {
        it.copy(text = text, parsed = text.takeIf(String::isNotBlank)?.let(parser::parse), error = null)
    }

    fun submit() {
        val state = _state.value
        val parsed = state.parsed ?: return
        if (!state.canSubmit) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val task = NewTask(
                title = parsed.title,
                projectId = parsed.projectName?.let { name -> knownProjects.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id },
                priority = parsed.priority ?: Priority.NORMAL,
                dueAt = parsed.dueAt?.toInstant() ?: defaultDue(),
                repeatRule = parsed.repeat,
            )
            tasks.create(task)
                .onSuccess { _state.value = QuickAddUiState(); _added.send(Unit) }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message ?: "Couldn't add task") } }
        }
    }

    /** With no explicit day/time the task lands on today's list at the next full hour (latest 23:00). */
    private fun defaultDue(): Instant {
        val now = ZonedDateTime.now(clock)
        val nextHour = now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val latest = now.with(LocalTime.of(23, 0))
        return minOf(nextHour, latest).toInstant()
    }
}
