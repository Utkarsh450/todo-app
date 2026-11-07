package com.utkarsh.todo.feature.today.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.core.common.dayRange
import com.utkarsh.todo.core.common.formatDayCaption
import com.utkarsh.todo.core.common.formatTime
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.tasks.domain.Field
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import com.utkarsh.todo.feature.today.domain.TaskItem
import com.utkarsh.todo.feature.today.domain.TodayView
import com.utkarsh.todo.feature.today.domain.UndoPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val caption: String = "",
    val items: List<TaskItem> = emptyList(),
    val view: TodayView = TodayView.BLOCKS,
    val undo: UndoPrompt? = null,
) {
    val left: Int get() = items.count { !it.done }
    val pinned: TaskItem? get() = items.firstOrNull { it.pinned && !it.done }
}

private sealed interface UndoAction {
    data class Moved(val task: Task) : UndoAction
    data class Archived(val task: Task) : UndoAction
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val tasks: TasksRepository,
    private val projects: ProjectsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState(caption = clock.instant().formatDayCaption(clock.zone)))
    val uiState: StateFlow<TodayUiState> = _state.asStateFlow()

    private var current: List<Task> = emptyList()
    private var projectsById: Map<String, Project> = emptyMap()
    private var pendingUndo: UndoAction? = null

    init {
        refresh()
        viewModelScope.launch { tasks.changes.collect { load(clearError = false) } }
    }

    fun refresh() = load(clearError = true)

    /** Reloads from the server; [clearError] = false keeps a message explaining why we are reloading. */
    private fun load(clearError: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = if (clearError) null else it.errorMessage) }
            val range = clock.dayRange()
            val tasksResult = async { tasks.dueBetween(range.start, range.end) }
            val projectsResult = async { projects.projects() }
            projectsById = projectsResult.await().getOrDefault(emptyList()).associateBy { it.id }
            tasksResult.await()
                .onSuccess { current = it; publish() }
                .onFailure { e -> _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Couldn't load tasks") } }
        }
    }

    fun setView(view: TodayView) = _state.update { it.copy(view = view) }

    fun toggleDone(id: String) {
        val task = current.firstOrNull { it.id == id } ?: return
        val nowDone = !task.isDone
        replace(task.copy(status = if (nowDone) TaskStatus.DONE else TaskStatus.TODO))
        viewModelScope.launch {
            tasks.setDone(id, nowDone).onFailure { failAndReload("Couldn't update task") }
        }
    }

    /** Swipe right: push the task to the same time tomorrow. */
    fun moveToTomorrow(id: String) {
        val task = current.firstOrNull { it.id == id } ?: return
        val due = task.dueAt ?: return
        remove(id)
        pendingUndo = UndoAction.Moved(task)
        _state.update { it.copy(undo = UndoPrompt("Moved to tomorrow")) }
        viewModelScope.launch {
            tasks.update(id, TaskPatch(dueAt = Field.Set(due.plus(Duration.ofDays(1)))))
                .onFailure { failAndReload("Couldn't move task") }
        }
    }

    /** Swipe left: archive the task. */
    fun archive(id: String) {
        val task = current.firstOrNull { it.id == id } ?: return
        remove(id)
        pendingUndo = UndoAction.Archived(task)
        _state.update { it.copy(undo = UndoPrompt("Archived")) }
        viewModelScope.launch {
            tasks.update(id, TaskPatch(archived = Field.Set(true))).onFailure { failAndReload("Couldn't archive task") }
        }
    }

    fun undo() {
        val action = pendingUndo ?: return
        pendingUndo = null
        _state.update { it.copy(undo = null) }
        viewModelScope.launch {
            when (action) {
                is UndoAction.Moved -> tasks.update(action.task.id, TaskPatch(dueAt = Field.Set(action.task.dueAt)))
                is UndoAction.Archived -> tasks.update(action.task.id, TaskPatch(archived = Field.Set(false)))
            }.onSuccess { restored ->
                val today = clock.dayRange()
                if (!restored.archived && restored.dueAt?.let { it in today } == true) {
                    current = (current.filterNot { it.id == restored.id } + restored).sortedBy { it.dueAt }
                    publish()
                }
            }.onFailure { failAndReload("Couldn't undo") }
        }
    }

    fun dismissUndo() {
        pendingUndo = null
        _state.update { it.copy(undo = null) }
    }

    fun dismissError() = _state.update { it.copy(errorMessage = null) }

    private fun replace(task: Task) {
        current = current.map { if (it.id == task.id) task else it }
        publish()
    }

    private fun remove(id: String) {
        current = current.filterNot { it.id == id }
        publish()
    }

    private fun failAndReload(message: String) {
        _state.update { it.copy(errorMessage = message, undo = null) }
        pendingUndo = null
        load(clearError = false)
    }

    private fun publish() = _state.update { state ->
        state.copy(isLoading = false, items = current.map(::toItem))
    }

    private fun toItem(task: Task): TaskItem {
        val project = task.projectId?.let(projectsById::get)
        return TaskItem(
            id = task.id,
            title = task.title,
            timeLabel = task.dueAt?.formatTime(clock.zone).orEmpty(),
            done = task.isDone,
            priority = task.priority,
            pinned = task.pinned,
            projectName = project?.name,
            projectColorHex = project?.colorHex,
            subtasksDone = task.subtasksDone,
            subtasksTotal = task.subtasks.size,
        )
    }
}
