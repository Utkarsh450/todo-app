package com.utkarsh.todo.feature.reminders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.feature.quickadd.domain.QuickAddParser
import com.utkarsh.todo.feature.quickadd.ui.QuickAddUiState
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
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

/** Adds a reminder from free text, reusing the quick-add token parser (`Standup tomorrow 9am every monday`). */
@HiltViewModel
class AddReminderViewModel @Inject constructor(
    private val repository: RemindersRepository,
    private val parser: QuickAddParser,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickAddUiState())
    val uiState: StateFlow<QuickAddUiState> = _state.asStateFlow()

    private val _added = Channel<Unit>(Channel.BUFFERED)
    val added: Flow<Unit> = _added.receiveAsFlow()

    fun onTextChange(text: String) = _state.update {
        it.copy(text = text, parsed = text.takeIf(String::isNotBlank)?.let(parser::parse), error = null)
    }

    fun submit() {
        val state = _state.value
        val parsed = state.parsed ?: return
        if (!state.canSubmit) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val reminder = NewReminder(
                title = parsed.title,
                remindAt = parsed.dueAt?.toInstant() ?: nextFullHour(),
                repeatRule = parsed.repeat,
            )
            repository.create(reminder)
                .onSuccess { _state.value = QuickAddUiState(); _added.send(Unit) }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message ?: "Couldn't add reminder") } }
        }
    }

    private fun nextFullHour(): Instant = ZonedDateTime.now(clock).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()
}
