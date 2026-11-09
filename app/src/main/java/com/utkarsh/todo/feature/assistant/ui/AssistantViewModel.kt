package com.utkarsh.todo.feature.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.core.common.formatRelativeDue
import com.utkarsh.todo.feature.ai.domain.AiException
import com.utkarsh.todo.feature.ai.domain.AiRepository
import com.utkarsh.todo.feature.ai.domain.AssistantSuggestion
import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import com.utkarsh.todo.feature.calendar.domain.NewEvent
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.tasks.domain.Field
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SuggestionStatus { PENDING, WORKING, ACCEPTED, SKIPPED, FAILED }

enum class SuggestionKind(val label: String) {
    MOVE("Move"), SPLIT("Split"), PRIORITY("Priority"), COMPLETE("Done"), TASK("Task"), EVENT("Event"), REMINDER("Reminder"),
}

data class SuggestionUi(
    val id: Int,
    val kind: SuggestionKind,
    val title: String,
    val reason: String,
    /** Only for a split: the steps that would be added, shown before the person accepts. */
    val steps: List<String> = emptyList(),
    val status: SuggestionStatus = SuggestionStatus.PENDING,
    /** What is applied on accept. A split that half-worked keeps only the steps still to add. */
    internal val change: AssistantSuggestion,
) {
    val actionable: Boolean get() = status == SuggestionStatus.PENDING || status == SuggestionStatus.FAILED
}

data class TurnUi(
    val id: Int,
    val question: String,
    val loading: Boolean = true,
    val reply: String? = null,
    val suggestions: List<SuggestionUi> = emptyList(),
    val error: String? = null,
    val notConfigured: Boolean = false,
) {
    val pendingCount: Int get() = suggestions.count { it.actionable }
}

data class AssistantUiState(
    val turns: List<TurnUi> = emptyList(),
    val input: String = "",
) {
    val busy: Boolean get() = turns.lastOrNull()?.loading == true
}

/**
 * A short conversation with the assistant. It answers with words and, when useful, suggestions; a suggestion
 * only changes a task when the person accepts it, and then through the same repository calls as the rest of the app.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val ai: AiRepository,
    private val tasks: TasksRepository,
    private val calendar: CalendarRepository,
    private val reminders: RemindersRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _state.asStateFlow()
    private var nextTurnId = 1
    private var nextSuggestionId = 1

    fun onInputChange(text: String) = _state.update { it.copy(input = text) }

    /** Sends the typed text, or [prompt] when a quick prompt is tapped (a half-typed draft is left alone). */
    fun ask(prompt: String? = null) {
        val question = (prompt ?: _state.value.input).trim()
        if (question.isEmpty() || _state.value.busy) return
        val turn = TurnUi(id = nextTurnId++, question = question)
        _state.update { it.copy(turns = it.turns + turn, input = if (prompt == null) "" else it.input) }
        fetch(turn.id, question)
    }

    fun retry(turnId: Int) {
        val turn = _state.value.turns.firstOrNull { it.id == turnId } ?: return
        if (_state.value.busy || turn.error == null) return
        updateTurn(turnId) { it.copy(loading = true, error = null, notConfigured = false) }
        fetch(turnId, turn.question)
    }

    fun skip(suggestionId: Int) = updateSuggestion(suggestionId) { if (it.actionable) it.copy(status = SuggestionStatus.SKIPPED) else it }

    fun accept(suggestionId: Int) {
        if (!claim(setOf(suggestionId))) return
        viewModelScope.launch { apply(suggestionId) }
    }

    fun acceptAll(turnId: Int) {
        val ids = _state.value.turns.firstOrNull { it.id == turnId }?.suggestions?.filter { it.actionable }?.map { it.id }.orEmpty().toSet()
        if (ids.isEmpty() || !claim(ids)) return
        viewModelScope.launch { ids.sorted().forEach { apply(it) } }
    }

    private fun fetch(turnId: Int, question: String) {
        viewModelScope.launch {
            ai.assist(question, clock.zone)
                .onSuccess { reply ->
                    val today = LocalDate.now(clock)
                    updateTurn(turnId) {
                        it.copy(loading = false, reply = reply.reply, suggestions = reply.suggestions.map { s -> toUi(s, today) })
                    }
                }
                .onFailure { e ->
                    updateTurn(turnId) {
                        it.copy(loading = false, error = e.message ?: "Couldn't reach the assistant", notConfigured = (e as? AiException)?.notConfigured == true)
                    }
                }
        }
    }

    /** Marks the given suggestions as being applied, but only those still actionable. False if none were. */
    private fun claim(ids: Set<Int>): Boolean {
        var claimed = false
        _state.update { state ->
            state.copy(
                turns = state.turns.map { turn ->
                    turn.copy(
                        suggestions = turn.suggestions.map {
                            if (it.id in ids && it.actionable) it.copy(status = SuggestionStatus.WORKING).also { claimed = true } else it
                        },
                    )
                },
            )
        }
        return claimed
    }

    private suspend fun apply(suggestionId: Int) {
        val suggestion = find(suggestionId)?.takeIf { it.status == SuggestionStatus.WORKING } ?: return
        when (val change = suggestion.change) {
            is AssistantSuggestion.Move -> finish(suggestionId, tasks.update(change.taskId, TaskPatch(dueAt = Field.Set(change.dueAt))).map { })
            is AssistantSuggestion.ChangePriority ->
                finish(suggestionId, tasks.update(change.taskId, TaskPatch(priority = Field.Set(change.priority))).map { })
            is AssistantSuggestion.Complete -> finish(suggestionId, tasks.setDone(change.taskId, true).map { })
            is AssistantSuggestion.CreateTask -> finish(
                suggestionId,
                tasks.create(NewTask(title = change.title, projectId = change.projectId, priority = change.priority, dueAt = change.dueAt, repeatRule = change.repeatRule)).map { },
            )
            is AssistantSuggestion.CreateEvent ->
                finish(suggestionId, calendar.createEvent(NewEvent(change.title, change.startAt, change.endAt, change.allDay)).map { })
            is AssistantSuggestion.CreateReminder ->
                finish(suggestionId, reminders.create(NewReminder(title = change.title, remindAt = change.remindAt, repeatRule = change.repeatRule)).map { })
            is AssistantSuggestion.Split -> {
                var added = 0
                var failure: Throwable? = null
                for (step in change.steps) {
                    val result = tasks.addSubtask(change.taskId, step)
                    if (result.isFailure) { failure = result.exceptionOrNull(); break }
                    added++
                }
                if (failure == null) {
                    finish(suggestionId, Result.success(Unit))
                } else {
                    // Keep only the steps not yet added, so trying again can't create duplicates.
                    val rest = change.steps.drop(added)
                    updateSuggestion(suggestionId) {
                        it.copy(status = SuggestionStatus.FAILED, steps = rest, change = change.copy(steps = rest))
                    }
                }
            }
        }
    }

    private fun finish(suggestionId: Int, result: Result<Unit>) = updateSuggestion(suggestionId) {
        it.copy(status = if (result.isSuccess) SuggestionStatus.ACCEPTED else SuggestionStatus.FAILED)
    }

    private fun find(suggestionId: Int): SuggestionUi? = _state.value.turns.flatMap { it.suggestions }.firstOrNull { it.id == suggestionId }

    private fun updateTurn(turnId: Int, change: (TurnUi) -> TurnUi) =
        _state.update { s -> s.copy(turns = s.turns.map { if (it.id == turnId) change(it) else it }) }

    private fun updateSuggestion(suggestionId: Int, change: (SuggestionUi) -> SuggestionUi) = _state.update { s ->
        s.copy(turns = s.turns.map { turn -> turn.copy(suggestions = turn.suggestions.map { if (it.id == suggestionId) change(it) else it }) })
    }

    private fun toUi(s: AssistantSuggestion, today: LocalDate): SuggestionUi = when (s) {
        is AssistantSuggestion.Move ->
            SuggestionUi(nextSuggestionId++, SuggestionKind.MOVE, "${s.taskTitle} → ${s.dueAt.formatRelativeDue(clock.zone, today)}", s.reason, change = s)
        is AssistantSuggestion.Split ->
            SuggestionUi(nextSuggestionId++, SuggestionKind.SPLIT, s.taskTitle, s.reason, steps = s.steps, change = s)
        is AssistantSuggestion.ChangePriority ->
            SuggestionUi(nextSuggestionId++, SuggestionKind.PRIORITY, "${s.taskTitle} → ${s.priority.name.lowercase().replaceFirstChar(Char::uppercase)}", s.reason, change = s)
        is AssistantSuggestion.Complete -> SuggestionUi(nextSuggestionId++, SuggestionKind.COMPLETE, s.taskTitle, s.reason, change = s)
        is AssistantSuggestion.CreateTask -> {
            val extras = listOfNotNull(s.dueAt?.formatRelativeDue(clock.zone, today), s.repeatRule, s.projectName?.let { "#$it" }, s.priority.takeIf { it != Priority.NORMAL }?.name?.lowercase())
            SuggestionUi(nextSuggestionId++, SuggestionKind.TASK, listOf(s.title).plus(extras.joinToString(" · ").takeIf { it.isNotEmpty() }).filterNotNull().joinToString(" — "), s.reason, change = s)
        }
        is AssistantSuggestion.CreateEvent ->
            SuggestionUi(nextSuggestionId++, SuggestionKind.EVENT, "${s.title} — ${s.startAt.formatRelativeDue(clock.zone, today)}${if (s.allDay) " · all day" else ""}", s.reason, change = s)
        is AssistantSuggestion.CreateReminder ->
            SuggestionUi(nextSuggestionId++, SuggestionKind.REMINDER, "${s.title} — ${s.remindAt.formatRelativeDue(clock.zone, today)}${s.repeatRule?.let { " · $it" }.orEmpty()}", s.reason, change = s)
    }
}
