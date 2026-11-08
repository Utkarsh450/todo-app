package com.utkarsh.todo.feature.braindump.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.core.common.formatRelativeDue
import com.utkarsh.todo.feature.ai.domain.AiException
import com.utkarsh.todo.feature.ai.domain.AiRepository
import com.utkarsh.todo.feature.ai.domain.DumpedTask
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Priority
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

enum class DumpPhase { INPUT, SORTING, RESULTS }

data class DumpRow(
    val id: Int,
    val title: String,
    /** "Friday, 10:00 AM" style label, or null when there is no time. */
    val dueLabel: String?,
    val priority: Priority,
    val repeat: String?,
    val projectName: String?,
    val projectColorHex: String?,
    val selected: Boolean = true,
    internal val task: DumpedTask,
)

data class BrainDumpUiState(
    val text: String = "",
    val phase: DumpPhase = DumpPhase.INPUT,
    val rows: List<DumpRow> = emptyList(),
    /** A problem or hint to show: a failed sort, "no tasks found", or a partly failed add. */
    val message: String? = null,
    val notConfigured: Boolean = false,
    val adding: Boolean = false,
) {
    val selectedCount: Int get() = rows.count { it.selected }
}

/**
 * Turns a rambling note (typed or dictated) into tasks. Grok sorts the note; the person unticks what they don't
 * want; only then are tasks created, one by one through the normal repository.
 */
@HiltViewModel
class BrainDumpViewModel @Inject constructor(
    private val ai: AiRepository,
    private val tasks: TasksRepository,
    private val projects: ProjectsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(BrainDumpUiState())
    val uiState: StateFlow<BrainDumpUiState> = _state.asStateFlow()

    private val _added = Channel<Unit>(Channel.BUFFERED)
    /** Emits once every chosen task was created, so the screen can close. */
    val added: Flow<Unit> = _added.receiveAsFlow()

    private var nextRowId = 1

    fun onTextChange(text: String) = _state.update { it.copy(text = text, message = null, notConfigured = false) }

    /** Adds dictated words after what is already there. */
    fun appendSpoken(spoken: String) {
        val add = spoken.trim()
        if (add.isEmpty()) return
        _state.update { s ->
            val joined = if (s.text.isBlank()) add else s.text.trimEnd() + " " + add
            s.copy(text = joined, message = null, notConfigured = false)
        }
    }

    fun startOver() {
        if (_state.value.adding || _state.value.phase == DumpPhase.SORTING) return
        _state.value = BrainDumpUiState()
    }

    /** Goes back to the note to change it, keeping the words. */
    fun editNote() {
        if (_state.value.adding || _state.value.phase != DumpPhase.RESULTS) return
        _state.update { it.copy(phase = DumpPhase.INPUT, rows = emptyList(), message = null) }
    }

    fun sort() {
        val current = _state.value
        val note = current.text.trim()
        if (note.isEmpty() || current.phase != DumpPhase.INPUT) return
        viewModelScope.launch {
            _state.update { it.copy(phase = DumpPhase.SORTING, message = null, notConfigured = false) }
            ai.brainDump(note, clock.zone)
                .onSuccess { found ->
                    if (found.isEmpty()) {
                        _state.update { it.copy(phase = DumpPhase.INPUT, message = "I couldn't find any tasks in that. Try adding a little more detail.") }
                    } else {
                        val colors = projects.projects().getOrNull().orEmpty().associate { it.id to it.colorHex }
                        val today = LocalDate.now(clock)
                        _state.update { it.copy(phase = DumpPhase.RESULTS, rows = found.map { t -> toRow(t, colors, today) }) }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(phase = DumpPhase.INPUT, message = e.message ?: "Couldn't sort that", notConfigured = (e as? AiException)?.notConfigured == true)
                    }
                }
        }
    }

    fun toggle(index: Int) = _state.update { s ->
        if (s.adding || index !in s.rows.indices) s
        else s.copy(rows = s.rows.mapIndexed { i, r -> if (i == index) r.copy(selected = !r.selected) else r }, message = null)
    }

    fun addSelected() {
        val current = _state.value
        if (current.adding || current.phase != DumpPhase.RESULTS || current.selectedCount == 0) return
        viewModelScope.launch {
            _state.update { it.copy(adding = true, message = null) }
            val chosen = _state.value.rows.filter { it.selected }
            var created = 0
            for (row in chosen) {
                val t = row.task
                val result = tasks.create(NewTask(title = t.title, projectId = t.projectId, priority = t.priority, dueAt = t.dueAt, repeatRule = t.repeatRule))
                if (result.isFailure) break
                created++
            }
            if (created == chosen.size) {
                _added.send(Unit)
            } else {
                // Keep only what still needs creating, so trying again can't make duplicates.
                val left = chosen.drop(created)
                _state.update { it.copy(adding = false, rows = left, message = "Added $created of ${chosen.size}. Couldn't add the rest.") }
            }
        }
    }

    private fun toRow(t: DumpedTask, colors: Map<String, String>, today: LocalDate) = DumpRow(
        id = nextRowId++,
        title = t.title,
        dueLabel = t.dueAt?.formatRelativeDue(clock.zone, today),
        priority = t.priority,
        repeat = t.repeatRule,
        projectName = t.projectName,
        projectColorHex = t.projectId?.let(colors::get),
        task = t,
    )
}
