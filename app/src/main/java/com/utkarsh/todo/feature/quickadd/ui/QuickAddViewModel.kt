package com.utkarsh.todo.feature.quickadd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import com.utkarsh.todo.feature.calendar.domain.NewEvent
import com.utkarsh.todo.feature.notes.domain.NotesRepository
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.quickadd.domain.QuickAddParser
import com.utkarsh.todo.feature.quickadd.domain.QuickAddResult
import com.utkarsh.todo.feature.quickadd.domain.QuickAddType
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
    val type: QuickAddType = QuickAddType.TASK,
    val text: String = "",
    val parsed: QuickAddResult? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = !isSaving && !parsed?.title.isNullOrBlank()

    /** An event given a day but no time lasts the whole day. */
    val isAllDayEvent: Boolean get() = type == QuickAddType.EVENT && parsed?.dueAt != null && !parsed.timeSpecified
}

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val tasks: TasksRepository,
    private val projects: ProjectsRepository,
    private val calendar: CalendarRepository,
    private val notes: NotesRepository,
    private val parser: QuickAddParser,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickAddUiState())
    val uiState: StateFlow<QuickAddUiState> = _state.asStateFlow()

    private val _added = Channel<Unit>(Channel.BUFFERED)
    /** Emits once a task, note or event was created, so the sheet can close. */
    val added: Flow<Unit> = _added.receiveAsFlow()

    private var knownProjects: List<Project> = emptyList()

    init {
        viewModelScope.launch { knownProjects = projects.projects().getOrDefault(emptyList()) }
    }

    fun onTextChange(text: String) = _state.update {
        it.copy(text = text, parsed = parse(text, it.type), error = null)
    }

    /** Switching type keeps what was typed and reads it again by the new type's rules. */
    fun onTypeChange(type: QuickAddType) = _state.update {
        it.copy(type = type, parsed = parse(it.text, type), error = null)
    }

    /** A note is kept exactly as typed; the other types look for days, projects and so on. */
    private fun parse(text: String, type: QuickAddType): QuickAddResult? = when {
        text.isBlank() -> null
        type == QuickAddType.NOTE -> QuickAddResult(title = text.trim(), dueAt = null, projectName = null, priority = null, repeat = null, tokens = emptyList())
        else -> parser.parse(text, type.kinds)
    }

    fun submit() {
        val state = _state.value
        val parsed = state.parsed ?: return
        if (!state.canSubmit) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = when (state.type) {
                QuickAddType.TASK -> tasks.create(taskFrom(parsed)).map { }
                QuickAddType.EVENT -> calendar.createEvent(eventFrom(parsed))
                QuickAddType.NOTE -> notes.append(LocalDate.now(clock), parsed.title)
            }
            result
                .onSuccess { _state.value = QuickAddUiState(); _added.send(Unit) }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message ?: "Couldn't add ${state.type.noun}") } }
        }
    }

    private fun projectIdOf(name: String?): String? =
        name?.let { n -> knownProjects.firstOrNull { it.name.equals(n, ignoreCase = true) }?.id }

    private fun taskFrom(parsed: QuickAddResult) = NewTask(
        title = parsed.title,
        projectId = projectIdOf(parsed.projectName),
        priority = parsed.priority ?: Priority.NORMAL,
        dueAt = parsed.dueAt?.toInstant() ?: defaultDue(),
        repeatRule = parsed.repeat,
    )

    /**
     * A day with a time is a one-hour event; a bare day is an all-day event; no date at all means
     * one hour starting at the next full hour, like a task.
     */
    private fun eventFrom(parsed: QuickAddResult): NewEvent {
        val projectId = projectIdOf(parsed.projectName)
        val due = parsed.dueAt
        if (due != null && !parsed.timeSpecified) {
            val day = due.toLocalDate()
            return NewEvent(
                title = parsed.title,
                start = day.atStartOfDay(clock.zone).toInstant(),
                end = day.atTime(LocalTime.of(23, 59)).atZone(clock.zone).toInstant(),
                allDay = true,
                projectId = projectId,
            )
        }
        val start = due?.toInstant() ?: defaultDue()
        return NewEvent(parsed.title, start, start.plus(Duration.ofHours(1)), allDay = false, projectId = projectId)
    }

    /** With no explicit day/time the task lands on today's list at the next full hour (latest 23:00). */
    private fun defaultDue(): Instant {
        val now = ZonedDateTime.now(clock)
        val nextHour = now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val latest = now.with(LocalTime.of(23, 0))
        return minOf(nextHour, latest).toInstant()
    }
}

private val QuickAddType.noun: String
    get() = when (this) {
        QuickAddType.TASK -> "task"
        QuickAddType.NOTE -> "note"
        QuickAddType.EVENT -> "event"
    }
