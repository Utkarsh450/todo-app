package com.utkarsh.todo.feature.today.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.core.common.DayRange
import com.utkarsh.todo.core.common.dayRange
import com.utkarsh.todo.core.common.formatDayCaption
import com.utkarsh.todo.core.common.formatTime
import com.utkarsh.todo.core.offline.cacheFirst
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.reminders.domain.ReminderGroups
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.tasks.domain.Field
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import com.utkarsh.todo.feature.today.domain.TaskItem
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

/** What the "Due soon" banner on Today says: alerts that are overdue and alerts still to ring today. */
data class DueSoonSummary(val overdue: Int, val today: Int) {
    val isEmpty: Boolean get() = overdue == 0 && today == 0
}

data class TodayUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val caption: String = "",
    val items: List<TaskItem> = emptyList(),
    val dueSoon: DueSoonSummary = DueSoonSummary(0, 0),
    val undo: UndoPrompt? = null,
) {
    val left: Int get() = items.count { !it.done }
    val total: Int get() = items.size
    val doneCount: Int get() = items.count { it.done }

    /** 0..1 for the card's progress bar; an empty day has none. */
    val progress: Float get() = if (items.isEmpty()) 0f else doneCount / total.toFloat()

    /** The first task not done yet, in the order the list is shown. */
    val next: TaskItem? get() = items.firstOrNull { !it.done }
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
    private val reminders: RemindersRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState(caption = clock.instant().formatDayCaption(clock.zone)))
    val uiState: StateFlow<TodayUiState> = _state.asStateFlow()

    private var current: List<Task> = emptyList()
    private var projectsById: Map<String, Project> = emptyMap()
    private var pendingUndo: UndoAction? = null
    private var triedSavedCopy = false
    private var hasShownList = false

    init {
        refresh()
        viewModelScope.launch { tasks.changes.collect { load(clearError = false) } }
        // An alert made, changed or snoozed anywhere (Create, the assistant, an event, Due soon) only moves the banner.
        viewModelScope.launch { reminders.changes.collect { refreshDueSoon() } }
        // A project renamed or recoloured anywhere only changes the names and colours on the rows.
        viewModelScope.launch { projects.changes.collect { refreshProjects() } }
    }

    private suspend fun refreshDueSoon() {
        val range = clock.dayRange()
        reminders.grouped(clock.instant(), range.end, range.end).onSuccess { groups -> showDueSoon(groups) }
    }

    private suspend fun refreshProjects() {
        projects.projects().onSuccess { list ->
            projectsById = list.associateBy { it.id }
            if (hasShownList) publish()
        }
    }

    fun refresh() = load(clearError = true)

    /** Reloads from the server; [clearError] = false keeps a message explaining why we are reloading. */
    private fun load(clearError: Boolean) {
        viewModelScope.launch {
            val range = clock.dayRange()
            if (!triedSavedCopy) showSavedCopy(range)
            // Once any list (even an empty saved one) is on screen, a refresh behind it must not bring the skeleton back.
            _state.update { it.copy(isLoading = !hasShownList, errorMessage = if (clearError) null else it.errorMessage) }
            val tasksResult = async { tasks.dueBetween(range.start, range.end) }
            val projectsResult = async { projects.projects() }
            val dueSoonResult = async { reminders.grouped(clock.instant(), range.end, range.end) }
            projectsById = projectsResult.await().getOrDefault(emptyList()).associateBy { it.id }
            dueSoonResult.await().onSuccess { groups -> showDueSoon(groups) }
            tasksResult.await()
                .onSuccess { current = it; publish() }
                .onFailure { e -> _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Couldn't load tasks") } }
        }
    }

    /**
     * First load only: puts the last saved answer on screen at once (no network, no skeleton) while the real request
     * runs. Later loads never do this, so older rows can't come back after the person has changed something.
     * A day with no saved copy (a new day, a fresh install) simply keeps the skeleton until the server answers.
     */
    private suspend fun showSavedCopy(range: DayRange) {
        triedSavedCopy = true
        cacheFirst { projects.projects() }.onSuccess { list -> projectsById = list.associateBy { it.id } }
        cacheFirst { reminders.grouped(clock.instant(), range.end, range.end) }.onSuccess { groups -> showDueSoon(groups) }
        cacheFirst { tasks.dueBetween(range.start, range.end) }.onSuccess { saved ->
            current = saved
            publish()
        }
    }

    private fun showDueSoon(groups: ReminderGroups) =
        _state.update { it.copy(dueSoon = DueSoonSummary(groups.overdue.count { r -> r.enabled }, groups.today.count { r -> r.enabled })) }

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
        hasShownList = true
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
