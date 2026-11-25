package com.utkarsh.todo.feature.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.core.common.formatRelativeDue
import com.utkarsh.todo.feature.ai.domain.AiException
import com.utkarsh.todo.feature.ai.domain.AiRepository
import com.utkarsh.todo.core.offline.cacheFirst
import com.utkarsh.todo.feature.ai.domain.AssistantSuggestion
import com.utkarsh.todo.feature.ai.domain.HistoryPage
import com.utkarsh.todo.feature.ai.domain.StoredMessage
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
    /** The saved message this suggestion came with, and its position in the server's list (outcomes are reported with both). */
    val messageId: String? = null,
    val serverIndex: Int = -1,
) {
    val actionable: Boolean get() = status == SuggestionStatus.PENDING || status == SuggestionStatus.FAILED
}

/** A fact the assistant saved by itself in a turn ("Noted: ..."); [undone] once the owner took it back. */
data class NotedUi(val id: String, val text: String, val undone: Boolean = false)

data class TurnUi(
    val id: Int,
    val question: String,
    val loading: Boolean = true,
    val reply: String? = null,
    val suggestions: List<SuggestionUi> = emptyList(),
    val error: String? = null,
    val notConfigured: Boolean = false,
    /** The saved assistant message of this turn (null for an older server). */
    val messageId: String? = null,
    val noted: List<NotedUi> = emptyList(),
    /** Came from the saved conversation rather than from a question asked in this session. */
    val fromHistory: Boolean = false,
) {
    val pendingCount: Int get() = suggestions.count { it.actionable }
}

data class AssistantUiState(
    val turns: List<TurnUi> = emptyList(),
    val input: String = "",
    /** True while the saved conversation is being read (the screen shows a skeleton if there is nothing else to show). */
    val loadingHistory: Boolean = false,
    val hasOlder: Boolean = false,
    val loadingOlder: Boolean = false,
    /** A short message about something that did not work (e.g. an undo); dismissed by the screen. */
    val notice: String? = null,
) {
    val busy: Boolean get() = turns.lastOrNull()?.loading == true
}

private const val HISTORY_PAGE = 20

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

    private val _state = MutableStateFlow(AssistantUiState(loadingHistory = true))
    val uiState: StateFlow<AssistantUiState> = _state.asStateFlow()
    private var nextTurnId = 1
    private var nextSuggestionId = 1
    private var nextBefore: String? = null

    init {
        loadHistory()
    }

    fun onInputChange(text: String) = _state.update { it.copy(input = text) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    /** The saved conversation: the copy this phone already has first (instant), then the server's answer. */
    private fun loadHistory() {
        viewModelScope.launch {
            cacheFirst { ai.history(null, HISTORY_PAGE) }.onSuccess { showHistory(it) }
            ai.history(null, HISTORY_PAGE).onSuccess { showHistory(it) }
            _state.update { it.copy(loadingHistory = false) }
        }
    }

    /** Shows the newest page; questions asked meanwhile stay after it. */
    private fun showHistory(page: HistoryPage) {
        nextBefore = page.nextBefore
        val past = turnsOf(page.messages, LocalDate.now(clock))
        _state.update { s -> s.copy(turns = past + s.turns.filterNot { it.fromHistory }, hasOlder = page.nextBefore != null) }
    }

    /** Loads the page before the oldest one shown and puts it above. */
    fun loadOlder() {
        val before = nextBefore ?: return
        if (_state.value.loadingOlder) return
        _state.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            ai.history(before, HISTORY_PAGE)
                .onSuccess { page ->
                    nextBefore = page.nextBefore
                    val older = turnsOf(page.messages, LocalDate.now(clock))
                    _state.update { s -> s.copy(turns = older + s.turns, hasOlder = page.nextBefore != null, loadingOlder = false) }
                }
                .onFailure { _state.update { it.copy(loadingOlder = false) } }
        }
    }

    /** Takes back a fact the assistant noted by itself. A fact that is already gone counts as undone. */
    fun undoNoted(turnId: Int, memoryId: String) {
        setUndone(turnId, memoryId, true)
        viewModelScope.launch {
            ai.deleteMemory(memoryId).onFailure { e ->
                if ((e as? AiException)?.notFound != true) {
                    setUndone(turnId, memoryId, false)
                    _state.update { it.copy(notice = "Couldn't undo that. Try again.") }
                }
            }
        }
    }

    private fun setUndone(turnId: Int, memoryId: String, undone: Boolean) =
        updateTurn(turnId) { turn -> turn.copy(noted = turn.noted.map { if (it.id == memoryId) it.copy(undone = undone) else it }) }

    /** Pairs each question with its reply (the messages come newest first) into turns, oldest first. */
    private fun turnsOf(newestFirst: List<StoredMessage>, today: LocalDate): List<TurnUi> {
        val turns = mutableListOf<TurnUi>()
        var question: StoredMessage? = null
        for (message in newestFirst.asReversed()) {
            if (message.fromOwner) {
                question = message
            } else {
                val asked = question ?: continue // a reply whose question is on an older page
                question = null
                turns += TurnUi(
                    id = nextTurnId++,
                    question = asked.text,
                    loading = false,
                    reply = message.text,
                    messageId = message.id,
                    fromHistory = true,
                    suggestions = message.suggestions.mapIndexed { i, s ->
                        val serverIndex = message.suggestionIndexes.getOrElse(i) { i }
                        toUi(s, today).copy(
                            status = when (message.outcomes[serverIndex]) {
                                "accepted" -> SuggestionStatus.ACCEPTED
                                "skipped" -> SuggestionStatus.SKIPPED
                                else -> SuggestionStatus.PENDING
                            },
                            messageId = message.id,
                            serverIndex = serverIndex,
                        )
                    },
                )
            }
        }
        return turns
    }

    /** Tells the server what the owner did with a suggestion (fire and forget: a failure here must never get in the way). */
    private fun report(suggestion: SuggestionUi?, accepted: Boolean) {
        val messageId = suggestion?.messageId ?: return
        if (suggestion.serverIndex < 0) return
        viewModelScope.launch { ai.reportOutcome(messageId, suggestion.serverIndex, accepted) }
    }

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

    fun skip(suggestionId: Int) {
        val before = find(suggestionId)?.takeIf { it.actionable } ?: return
        updateSuggestion(suggestionId) { it.copy(status = SuggestionStatus.SKIPPED) }
        report(before, accepted = false)
    }

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
                        it.copy(
                            loading = false,
                            reply = reply.reply,
                            messageId = reply.messageId,
                            noted = reply.noted.map { fact -> NotedUi(fact.id, fact.text) },
                            suggestions = reply.suggestions.mapIndexed { i, s ->
                                toUi(s, today).copy(messageId = reply.messageId, serverIndex = reply.suggestionIndexes.getOrElse(i) { i })
                            },
                        )
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

    private fun finish(suggestionId: Int, result: Result<Unit>) {
        updateSuggestion(suggestionId) { it.copy(status = if (result.isSuccess) SuggestionStatus.ACCEPTED else SuggestionStatus.FAILED) }
        if (result.isSuccess) report(find(suggestionId), accepted = true)
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
