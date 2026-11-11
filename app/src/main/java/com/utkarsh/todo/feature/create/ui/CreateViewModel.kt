package com.utkarsh.todo.feature.create.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import com.utkarsh.todo.feature.calendar.domain.EventDetail
import com.utkarsh.todo.feature.calendar.domain.NewEvent
import com.utkarsh.todo.feature.create.domain.CreateForm
import com.utkarsh.todo.feature.create.domain.CreateType
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.reminders.domain.AlertStyle
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateUiState(
    val form: CreateForm,
    val projects: List<Project> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
    /** Set when an existing event is being edited (the type switch and the alerts are then left out). */
    val editingEventId: String? = null,
    val loading: Boolean = false,
)

/**
 * The Create screen (15): one form for a task or an event. Day, time, alerts and repeat are chips; "Pick date…"
 * and "Custom…" open the full pickers (16-18). Saving makes the task or event and then its alerts.
 */
@HiltViewModel
class CreateViewModel @Inject constructor(
    private val tasks: TasksRepository,
    private val calendar: CalendarRepository,
    private val reminders: RemindersRepository,
    private val projects: ProjectsRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val zone: ZoneId = clock.zone
    private val now: LocalDateTime get() = LocalDateTime.now(clock)

    // The route's arguments (see Destination.Create) arrive in the SavedStateHandle under their own names.
    private val editId: String? = savedState.get<String>("eventId")?.takeIf { it.isNotBlank() }
    private val _state = MutableStateFlow(CreateUiState(form = initialForm(savedState), editingEventId = editId, loading = editId != null))
    val uiState: StateFlow<CreateUiState> = _state.asStateFlow()

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    /** Emits once the task or event is saved, so the screen can close. */
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    init {
        viewModelScope.launch { projects.projects().onSuccess { list -> _state.update { it.copy(projects = list) } } }
        editId?.let { id ->
            viewModelScope.launch {
                calendar.event(id)
                    .onSuccess { d -> _state.update { it.copy(form = formOf(d), loading = false) } }
                    .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Couldn't open that event") } }
            }
        }
    }

    private fun formOf(d: EventDetail): CreateForm {
        val start = d.start.atZone(zone)
        return CreateForm(
            type = CreateType.EVENT, title = d.title, date = start.toLocalDate(), time = if (d.allDay) null else start.toLocalTime(),
            durationMinutes = java.time.Duration.between(d.start, d.end).toMinutes().toInt().coerceAtLeast(15), allDay = d.allDay,
            alerts = emptySet(), projectId = d.projectId, location = d.location.orEmpty(), videoLink = d.videoLink.orEmpty(), guests = d.guests, busy = d.busy,
        )
    }

    private fun initialForm(savedState: SavedStateHandle): CreateForm {
        val type = if (savedState.get<String>("type") == CreateType.EVENT.name) CreateType.EVENT else CreateType.TASK
        val start = savedState.get<Long>("startEpochSeconds")?.takeIf { it > 0 }?.let { Instant.ofEpochSecond(it).atZone(zone).toLocalDateTime() }
        val day = start?.toLocalDate() ?: savedState.get<Long>("epochDay")?.takeIf { it >= 0 }?.let(LocalDate::ofEpochDay) ?: LocalDate.now(clock)
        return CreateForm.start(type, day, start?.toLocalTime(), now)
    }

    private fun edit(change: (CreateForm) -> CreateForm) = _state.update { it.copy(form = change(it.form), error = null) }

    fun setType(type: CreateType) = edit { it.withType(type) }

    fun setTitle(title: String) = edit { it.copy(title = title) }

    fun setDate(date: LocalDate) = edit { it.copy(date = date) }

    /** Null clears the time (tasks only; an event always keeps one). */
    fun setTime(time: LocalTime?) = edit { form -> if (time == null && form.type == CreateType.EVENT) form else form.copy(time = time, allDay = false) }

    fun setDuration(minutes: Int) = edit { it.copy(durationMinutes = minutes, allDay = false, time = it.time ?: LocalTime.of(9, 0)) }

    fun setAllDay() = edit { it.copy(allDay = true) }

    /** Taps a chip on or off. */
    fun toggleAlert(minutes: Int) = edit { it.copy(alerts = if (minutes in it.alerts) it.alerts - minutes else it.alerts + minutes) }

    /** A custom alert from the Alerts sheet: switched on (never off), whatever its length. */
    fun addAlert(minutes: Int) = edit { it.copy(alerts = it.alerts + minutes) }

    fun setRepeat(rule: String?) = edit { it.copy(repeat = rule) }

    fun setAlertStyle(style: AlertStyle) = edit { it.copy(alertStyle = style) }

    fun setLocation(text: String) = edit { it.copy(location = text) }

    fun setVideoLink(text: String) = edit { it.copy(videoLink = text) }

    fun setBusy(busy: Boolean) = edit { it.copy(busy = busy) }

    /** Adds a guest (an email or a name); blanks and repeats are ignored. */
    fun addGuest(text: String) = edit { form ->
        val guest = text.trim()
        if (guest.isEmpty() || form.guests.any { it.equals(guest, ignoreCase = true) }) form else form.copy(guests = form.guests + guest)
    }

    fun removeGuest(guest: String) = edit { it.copy(guests = it.guests - guest) }

    fun setNotes(notes: String) = edit { it.copy(notes = notes) }

    fun setPriority(priority: Priority) = edit { it.copy(priority = priority) }

    /** Tapping the chosen project again clears it. */
    fun toggleProject(id: String) = edit { it.copy(projectId = if (it.projectId == id) null else id) }

    fun save() {
        val state = _state.value
        if (state.saving) return
        val form = state.form
        form.problem()?.let { message -> _state.update { it.copy(error = message) }; return }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val title = form.title.trim()
            if (editId != null) {
                val changed = NewEvent(title, form.startInstant(zone), form.endInstant(zone), form.allDay, form.projectId, emptyList(), form.alertStyle,
                    form.location.trim().ifEmpty { null }, form.videoLink.trim().ifEmpty { null }, form.guests, form.busy)
                calendar.updateEvent(editId, changed)
                    .onSuccess { _saved.send(Unit) }
                    .onFailure { e -> _state.update { it.copy(saving = false, error = e.message ?: "Couldn't save the event") } }
                return@launch
            }
            val result: Result<Unit> = when (form.type) {
                CreateType.TASK -> tasks.create(NewTask(title = title, notes = form.notes.trim(), projectId = form.projectId, priority = form.priority, dueAt = form.startInstant(zone), repeatRule = form.repeat))
                    .onSuccess { task -> form.alertInstants(zone, clock.instant()).forEach { at -> reminders.create(NewReminder(title = title, remindAt = at, taskId = task.id, repeatRule = form.repeat, style = form.alertStyle)) } }
                    .map { }
                CreateType.EVENT -> calendar.createEvent(NewEvent(title, form.startInstant(zone), form.endInstant(zone), form.allDay, form.projectId, form.alerts.toList(), form.alertStyle,
                    form.location.trim().ifEmpty { null }, form.videoLink.trim().ifEmpty { null }, form.guests, form.busy)).map { }
            }
            result
                .onSuccess { _saved.send(Unit) }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message ?: "Couldn't save the ${form.type.label.lowercase()}") } }
        }
    }
}
