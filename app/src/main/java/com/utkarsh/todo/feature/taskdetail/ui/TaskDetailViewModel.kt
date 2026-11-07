package com.utkarsh.todo.feature.taskdetail.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.core.common.formatRelativeDue
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Subtask
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val clock: Clock,
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"]) { "taskId argument missing" }

    private val _state = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _state.asStateFlow()

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
                .onSuccess { task -> _state.value = TaskDetailUiState(isLoading = false, task = toUi(task)) }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load task") } }
        }
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

    fun delete() {
        viewModelScope.launch {
            tasks.delete(taskId).onSuccess { _closed.send(Unit) }.onFailure { fail("Couldn't delete task") }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun mutate(call: suspend () -> Result<Task>) {
        viewModelScope.launch {
            call().onSuccess { task -> _state.update { it.copy(task = toUi(task), error = null) } }.onFailure { fail("Couldn't update task") }
        }
    }

    private fun fail(message: String) = _state.update { it.copy(error = message) }

    private suspend fun toUi(task: Task): TaskDetailUi {
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
            notes = task.notes,
            subtasks = task.subtasks,
            done = task.isDone,
        )
    }
}
