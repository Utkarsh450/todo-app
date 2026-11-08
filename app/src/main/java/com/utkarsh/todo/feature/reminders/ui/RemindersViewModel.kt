package com.utkarsh.todo.feature.reminders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.core.common.dayRange
import com.utkarsh.todo.core.common.formatTime
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.ReminderGroups
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.today.domain.UndoPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SectionKind { OVERDUE, TODAY, THIS_WEEK, LATER }

data class ReminderRow(
    val id: String,
    val time: String,
    val meridiem: String,
    val title: String,
    val meta: String?,
    val repeat: String?,
    val location: String?,
    val enabled: Boolean,
)

data class ReminderSection(val kind: SectionKind, val label: String, val rows: List<ReminderRow>)

/** Next-up card; [snoozed] switches the tag to "SNOOZED". */
data class HeroUi(val id: String, val title: String, val subtitle: String, val snoozed: Boolean)

enum class SnoozeOption(val label: String) { TEN_MINUTES("10 min"), ONE_HOUR("1 hour"), TOMORROW("Tomorrow") }

data class RemindersUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val hero: HeroUi? = null,
    val sections: List<ReminderSection> = emptyList(),
    val undo: UndoPrompt? = null,
)

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repository: RemindersRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(RemindersUiState())
    val uiState: StateFlow<RemindersUiState> = _state.asStateFlow()

    private var groups = ReminderGroups()
    private var deleted: Reminder? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val today = LocalDate.now(clock)
            val endOfWeek = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(clock.zone).toInstant()
            repository.grouped(clock.instant(), clock.dayRange().end, endOfWeek)
                .onSuccess { groups = it; publish() }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load reminders") } }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) = act("Couldn't update reminder") { repository.setEnabled(id, enabled) }

    /** Swipe right on a row: snooze one hour. */
    fun snoozeRow(id: String) = act("Couldn't snooze reminder") { repository.snooze(id, 60) }

    fun snoozeHero(option: SnoozeOption) {
        val id = _state.value.hero?.id ?: return
        act("Couldn't snooze reminder") {
            when (option) {
                SnoozeOption.TEN_MINUTES -> repository.snooze(id, 10)
                SnoozeOption.ONE_HOUR -> repository.snooze(id, 60)
                SnoozeOption.TOMORROW -> repository.snoozeUntil(id, tomorrowMorning())
            }
        }
    }

    /** Swipe left on a row: delete, with undo that re-creates the reminder. */
    fun delete(id: String) {
        val reminder = groups.all.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            repository.delete(id)
                .onSuccess {
                    deleted = reminder
                    _state.update { it.copy(undo = UndoPrompt("Reminder deleted")) }
                    refresh()
                }
                .onFailure { _state.update { it.copy(error = "Couldn't delete reminder") } }
        }
    }

    fun undoDelete() {
        val reminder = deleted ?: return
        deleted = null
        _state.update { it.copy(undo = null) }
        viewModelScope.launch {
            repository.create(NewReminder(reminder.title, reminder.remindAt, reminder.taskId, reminder.repeatRule, reminder.locationTrigger))
                .onSuccess { refresh() }
                .onFailure { _state.update { it.copy(error = "Couldn't restore reminder") } }
        }
    }

    fun dismissUndo() {
        deleted = null
        _state.update { it.copy(undo = null) }
    }

    private fun act(errorMessage: String, call: suspend () -> Result<Reminder>) {
        viewModelScope.launch {
            call().onSuccess { refresh() }.onFailure { _state.update { s -> s.copy(error = errorMessage) } }
        }
    }

    private fun tomorrowMorning(): Instant = LocalDate.now(clock).plusDays(1).atTime(LocalTime.of(9, 0)).atZone(clock.zone).toInstant()

    private fun publish() {
        val sections = listOf(
            ReminderSection(SectionKind.OVERDUE, "Overdue", groups.overdue.map(::row)),
            ReminderSection(SectionKind.TODAY, "Today", groups.today.map(::row)),
            ReminderSection(SectionKind.THIS_WEEK, "This week", groups.thisWeek.map(::row)),
            ReminderSection(SectionKind.LATER, "Later", groups.later.map(::row)),
        ).filter { it.rows.isNotEmpty() }
        val next = (groups.today + groups.thisWeek + groups.later).firstOrNull { it.enabled && it.effectiveAt.isAfter(clock.instant()) }
        _state.update { it.copy(isLoading = false, sections = sections, hero = next?.let(::hero)) }
    }

    private fun row(r: Reminder): ReminderRow {
        val time = r.effectiveAt.formatTime(clock.zone) // "11:00 AM"
        val (clockPart, meridiem) = time.split(" ").let { it[0] to it.getOrElse(1) { "" } }
        return ReminderRow(
            id = r.id,
            time = clockPart,
            meridiem = meridiem,
            title = r.title,
            meta = when {
                r.snoozedUntil != null -> "Snoozed"
                !r.effectiveAt.isAfter(clock.instant()) || r.effectiveAt >= clock.dayRange().end -> dayLabel(r.effectiveAt)
                else -> null
            },
            repeat = r.repeatRule?.let { "↻ $it" },
            location = r.locationTrigger?.let { "⌖ $it" },
            enabled = r.enabled,
        )
    }

    private fun hero(r: Reminder): HeroUi {
        val minutes = Duration.between(clock.instant(), r.effectiveAt).toMinutes()
        val relative = when {
            minutes < 1 -> "Now"
            minutes < 60 -> "In $minutes min"
            minutes < 24 * 60 -> "In ${minutes / 60} h"
            else -> dayLabel(r.effectiveAt)
        }
        return HeroUi(r.id, r.title, "$relative · ${r.effectiveAt.formatTime(clock.zone)}", snoozed = r.snoozedUntil != null)
    }

    private fun dayLabel(at: Instant): String {
        val date = at.atZone(clock.zone).toLocalDate()
        return when (java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(clock), date)) {
            -1L -> "Yesterday"
            0L -> "Today"
            1L -> "Tomorrow"
            else -> DAY_FORMAT.format(date)
        }
    }

    private companion object {
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    }
}
