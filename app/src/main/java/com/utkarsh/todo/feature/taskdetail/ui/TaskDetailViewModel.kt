package com.utkarsh.todo.feature.taskdetail.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.core.common.formatRelativeDue
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.tasks.domain.Field
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Subtask
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RemindState { NEEDS_DUE, TOO_SOON, OFF, ON }

/** The REMIND row: whether a reminder one hour before the due time exists, and what to say about it. */
data class RemindUi(val state: RemindState, val label: String)

data class TaskDetailUi(
    val id: String,
    val title: String,
    val statusLabel: String,
    val dueLabel: String?,
    val priority: Priority,
    val projectName: String?,
    val projectColorHex: String?,
    val repeat: String?,
    val notes: String,
    val subtasks: List<Subtask>,
    val done: Boolean,
    val remind: RemindUi = RemindUi(RemindState.NEEDS_DUE, "Add a due date first"),
) {
    val subtasksDone: Int get() = subtasks.count { it.done }
}

data class TaskDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val task: TaskDetailUi? = null,
)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tasks: TasksRepository,
    private val projects: ProjectsRepository,
    private val reminders: RemindersRepository,
    private val clock: Clock,
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"]) { "taskId argument missing" }

    private val _state = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _state.asStateFlow()

    private var task: Task? = null
    private var reminder: Reminder? = null
    /** Notes typed but not yet saved; shown instead of the server's copy so a reload can't undo typing. */
    private var pendingNotes: String? = null
    private var notesJob: Job? = null

    private val _closed = Channel<Unit>(Channel.BUFFERED)
    /** Emits when the task was deleted and the screen should close. */
    val closed: Flow<Unit> = _closed.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            tasks.get(taskId)
                .onSuccess { loaded ->
                    reminder = reminders.forTask(taskId).getOrNull()
                    _state.value = TaskDetailUiState(isLoading = false, task = toUi(loaded))
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load task") } }
        }
    }

    /** Re-reads the task without the spinner, e.g. after the Break it down sheet added subtasks. */
    fun refresh() {
        viewModelScope.launch { tasks.get(taskId).onSuccess { loaded -> _state.update { it.copy(task = toUi(loaded)) } } }
    }

    fun toggleDone() {
        val task = _state.value.task ?: return
        mutate { tasks.setDone(taskId, !task.done) }
    }

    fun toggleSubtask(id: String) {
        val sub = _state.value.task?.subtasks?.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            tasks.setSubtaskDone(id, !sub.done)
                .onSuccess { updated ->
                    _state.update { s ->
                        s.copy(task = s.task?.copy(subtasks = s.task.subtasks.map { if (it.id == id) updated else it }))
                    }
                }
                .onFailure { fail("Couldn't update subtask") }
        }
    }

    fun addSubtask(title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            tasks.addSubtask(taskId, clean)
                .onSuccess { added -> _state.update { s -> s.copy(task = s.task?.copy(subtasks = s.task.subtasks + added)) } }
                .onFailure { fail("Couldn't add subtask") }
        }
    }

    /** Turns the "1 hour before due" reminder on or off. */
    fun toggleRemind() {
        val current = _state.value.task ?: return
        when (current.remind.state) {
            RemindState.ON -> viewModelScope.launch {
                val existing = reminder ?: return@launch
                reminders.delete(existing.id)
                    .onSuccess { reminder = null; refreshRemind() }
                    .onFailure { fail("Couldn't remove the reminder") }
            }
            RemindState.OFF -> viewModelScope.launch {
                val due = task?.dueAt ?: return@launch
                reminders.create(NewReminder(title = current.title, remindAt = due.minus(REMIND_BEFORE), taskId = taskId))
                    .onSuccess { reminder = it; refreshRemind() }
                    .onFailure { fail("Couldn't set the reminder") }
            }
            RemindState.NEEDS_DUE, RemindState.TOO_SOON -> Unit
        }
    }

    /** Saves notes shortly after typing stops, rather than on every keystroke. */
    fun onNotesChange(text: String) {
        pendingNotes = text
        _state.update { s -> s.copy(task = s.task?.copy(notes = text), error = null) }
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            delay(NOTES_SAVE_DELAY_MS)
            tasks.update(taskId, TaskPatch(notes = Field.Set(text)))
                .onSuccess { saved -> task = saved; if (pendingNotes == text) pendingNotes = null }
                .onFailure { fail("Couldn't save notes") }
        }
    }

    fun delete() {
        viewModelScope.launch {
            val alarmId = reminder?.id
            tasks.delete(taskId)
                .onSuccess {
                    alarmId?.let(reminders::forgetAlarm) // the server removed the reminder with the task; drop its device alarm
                    _closed.send(Unit)
                }
                .onFailure { fail("Couldn't delete task") }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun mutate(call: suspend () -> Result<Task>) {
        viewModelScope.launch {
            call().onSuccess { task -> _state.update { it.copy(task = toUi(task), error = null) } }.onFailure { fail("Couldn't update task") }
        }
    }

    private fun refreshRemind() = _state.update { s -> s.copy(task = s.task?.copy(remind = remindUi()), error = null) }

    private fun remindUi(): RemindUi {
        val due = task?.dueAt ?: return RemindUi(RemindState.NEEDS_DUE, "Add a due date first")
        reminder?.let { return RemindUi(RemindState.ON, "⏰ ${it.remindAt.formatRelativeDue(clock.zone, LocalDate.now(clock))}") }
        return if (due.minus(REMIND_BEFORE).isAfter(clock.instant())) RemindUi(RemindState.OFF, "+ 1h before due")
        else RemindUi(RemindState.TOO_SOON, "Due too soon")
    }

    private fun fail(message: String) = _state.update { it.copy(error = message) }

    private suspend fun toUi(task: Task): TaskDetailUi {
        this.task = task
        val project = task.projectId?.let { id -> projects.projects().getOrNull()?.firstOrNull { it.id == id } }
        return TaskDetailUi(
            id = task.id,
            title = task.title,
            statusLabel = when (task.status) {
                TaskStatus.TODO -> "TO DO"
                TaskStatus.IN_PROGRESS -> "IN PROGRESS"
                TaskStatus.DONE -> "DONE"
            },
            dueLabel = task.dueAt?.formatRelativeDue(clock.zone, LocalDate.now(clock)),
            priority = task.priority,
            projectName = project?.name,
            projectColorHex = project?.colorHex,
            repeat = task.repeatRule,
            notes = pendingNotes ?: task.notes,
            subtasks = task.subtasks,
            done = task.isDone,
            remind = remindUi(),
        )
    }

    companion object {
        const val NOTES_SAVE_DELAY_MS = 600L
        private val REMIND_BEFORE: Duration = Duration.ofHours(1)
    }
}
