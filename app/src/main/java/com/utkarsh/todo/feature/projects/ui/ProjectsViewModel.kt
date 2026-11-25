package com.utkarsh.todo.feature.projects.ui

import com.utkarsh.todo.core.offline.cacheFirst
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.core.common.formatTime
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectPalette
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectTaskUi(val id: String, val title: String, val done: Boolean, val timeLabel: String?)

data class ProjectRowUi(
    val id: String,
    val name: String,
    val colorHex: String,
    val done: Int,
    val total: Int,
    val tasks: List<ProjectTaskUi>,
) {
    /** 0..1 for the progress bar. */
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** The sheet used to make a project (id null) or rename / recolour one. */
data class ProjectEditorUi(val id: String?, val name: String = "", val colorHex: String = ProjectPalette.first(), val error: String? = null, val saving: Boolean = false) {
    val isNew: Boolean get() = id == null
}

data class ProjectsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val rows: List<ProjectRowUi> = emptyList(),
    val expandedId: String? = null,
    val editor: ProjectEditorUi? = null,
    val confirmDelete: ProjectRowUi? = null,
    /** A short message for a failed action (the screen stays as it was). */
    val message: String? = null,
) {
    val totalDone: Int get() = rows.sumOf { it.done }
    val total: Int get() = rows.sumOf { it.total }
}

/** Projects with their progress. Tap one to open its tasks and tick them off; make, rename or delete projects here. */
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projects: ProjectsRepository,
    private val tasks: TasksRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { tasks.changes.collect { load(showSkeleton = false) } }
    }

    private var triedSavedCopy = false
    private var hasShown = false

    fun load(showSkeleton: Boolean = true) {
        viewModelScope.launch {
            if (!triedSavedCopy) showSavedCopy()
            // Once any list (even an empty saved one) is on screen, a reload behind it must not bring the skeleton back.
            if (showSkeleton) _state.update { it.copy(isLoading = !hasShown, error = null) }
            val projectsResult = projects.projects()
            val tasksResult = tasks.all()
            val failure = projectsResult.exceptionOrNull() ?: tasksResult.exceptionOrNull()
            if (failure != null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = if (it.rows.isEmpty()) failure.message ?: "Couldn't load projects" else null,
                        message = if (it.rows.isEmpty()) null else "Couldn't refresh",
                    )
                }
                return@launch
            }
            val byProject = tasksResult.getOrThrow().filter { it.projectId != null }.groupBy { it.projectId }
            val rows = projectsResult.getOrThrow().map { it.toRow(byProject[it.id].orEmpty()) }
            hasShown = true
            _state.update { it.copy(isLoading = false, error = null, rows = rows, expandedId = it.expandedId?.takeIf { id -> rows.any { r -> r.id == id } }) }
        }
    }

    /**
     * First load only: puts the last saved answer on screen at once (no network, no skeleton) while the real request runs.
     * Later loads never do this, so older rows can't come back after the person has changed something. With nothing saved
     * the skeleton simply stays until the server answers.
     */
    private suspend fun showSavedCopy() {
        triedSavedCopy = true
        val (savedProjects, savedTasks) = cacheFirst { projects.projects() to tasks.all() }
        val projectList = savedProjects.getOrNull() ?: return
        val taskList = savedTasks.getOrNull() ?: return
        val byProject = taskList.filter { it.projectId != null }.groupBy { it.projectId }
        hasShown = true
        _state.update { it.copy(isLoading = false, rows = projectList.map { p -> p.toRow(byProject[p.id].orEmpty()) }) }
    }

    fun toggleExpanded(id: String) = _state.update { it.copy(expandedId = if (it.expandedId == id) null else id) }

    fun toggleTask(taskId: String) {
        val current = _state.value.rows.flatMap { it.tasks }.firstOrNull { it.id == taskId } ?: return
        viewModelScope.launch {
            tasks.setDone(taskId, !current.done)
                .onSuccess { updated ->
                    // Update in place so the list does not jump; the progress bar follows.
                    _state.update { s -> s.copy(rows = s.rows.map { row -> if (row.tasks.any { it.id == taskId }) row.withTask(updated) else row }) }
                }
                .onFailure { _state.update { it.copy(message = "Couldn't update the task") } }
        }
    }

    // ---- make / rename / recolour

    fun openNew() = _state.update { it.copy(editor = ProjectEditorUi(id = null, colorHex = nextFreeColour(it.rows))) }

    fun openRename(id: String) {
        val row = _state.value.rows.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(editor = ProjectEditorUi(id = row.id, name = row.name, colorHex = row.colorHex)) }
    }

    fun onEditorName(text: String) = _state.update { s -> s.copy(editor = s.editor?.copy(name = text, error = null)) }

    fun onEditorColor(hex: String) = _state.update { s -> s.copy(editor = s.editor?.copy(colorHex = hex)) }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    fun saveEditor() {
        val editor = _state.value.editor ?: return
        if (editor.saving) return
        val name = editor.name.trim()
        val problem = when {
            name.isEmpty() -> "Give it a name"
            name.length > MAX_NAME -> "Keep the name under $MAX_NAME characters"
            _state.value.rows.any { it.id != editor.id && it.name.equals(name, ignoreCase = true) } -> "A project with that name already exists"
            else -> null
        }
        if (problem != null) {
            _state.update { it.copy(editor = editor.copy(error = problem)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(editor = editor.copy(saving = true, error = null)) }
            val result = if (editor.id == null) projects.create(name, editor.colorHex) else projects.update(editor.id, name, editor.colorHex)
            result
                .onSuccess { saved ->
                    _state.update { s ->
                        val rows = if (editor.id == null) (s.rows + saved.toRow(emptyList())).sortedBy { it.name.lowercase() }
                        else s.rows.map { if (it.id == saved.id) it.copy(name = saved.name, colorHex = saved.colorHex) else it }.sortedBy { it.name.lowercase() }
                        s.copy(rows = rows, editor = null, expandedId = if (editor.id == null) saved.id else s.expandedId)
                    }
                }
                .onFailure { _state.update { it.copy(editor = editor.copy(saving = false, error = "Couldn't save the project")) } }
        }
    }

    // ---- delete

    fun askDelete(id: String) = _state.update { s -> s.copy(confirmDelete = s.rows.firstOrNull { it.id == id }) }

    fun cancelDelete() = _state.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val row = _state.value.confirmDelete ?: return
        viewModelScope.launch {
            projects.delete(row.id)
                .onSuccess { _state.update { s -> s.copy(rows = s.rows.filterNot { it.id == row.id }, confirmDelete = null, expandedId = s.expandedId?.takeIf { it != row.id }) } }
                .onFailure { _state.update { it.copy(confirmDelete = null, message = "Couldn't delete the project") } }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ---- mapping

    private fun Project.toRow(projectTasks: List<Task>) = ProjectRowUi(
        id = id, name = name, colorHex = colorHex,
        done = projectTasks.count { it.isDone }, total = projectTasks.size,
        tasks = projectTasks.sortedWith(compareBy({ it.isDone }, { it.dueAt })).map { it.toUi() },
    )

    private fun Task.toUi() = ProjectTaskUi(id, title, isDone, dueAt?.formatTime(clock.zone))

    private fun ProjectRowUi.withTask(updated: Task): ProjectRowUi {
        val list = tasks.map { if (it.id == updated.id) updated.toUi() else it }
        return copy(tasks = list, done = list.count { it.done })
    }

    /** The first palette colour not already used by a project, so new projects look different by default. */
    private fun nextFreeColour(rows: List<ProjectRowUi>): String =
        ProjectPalette.firstOrNull { c -> rows.none { it.colorHex.equals(c, ignoreCase = true) } } ?: ProjectPalette.first()

    companion object {
        const val MAX_NAME = 40
    }
}
