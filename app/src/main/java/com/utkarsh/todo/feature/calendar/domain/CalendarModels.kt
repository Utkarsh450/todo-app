package com.utkarsh.todo.feature.calendar.domain

import com.utkarsh.todo.feature.reminders.domain.AlertStyle
import java.time.Instant
import kotlinx.coroutines.flow.Flow

enum class AgendaKind { EVENT, TASK, REMINDER }

/** One entry on the calendar: a scheduled event, a task with a due time, or a stand-alone reminder. */
data class AgendaItem(
    val kind: AgendaKind,
    val id: String,
    val title: String,
    val start: Instant,
    val end: Instant?,
    val allDay: Boolean,
    val projectId: String?,
    val done: Boolean,
)

data class NewEvent(
    val title: String,
    val start: Instant,
    val end: Instant,
    val allDay: Boolean = false,
    val projectId: String? = null,
    /** Minutes before the start that an alert should ring (like Google Calendar's); empty for none. */
    val alerts: List<Int> = listOf(DEFAULT_ALERT_MINUTES),
    val alertStyle: AlertStyle = AlertStyle.ALARM,
    val location: String? = null,
    val videoLink: String? = null,
    val guests: List<String> = emptyList(),
    /** "Show as": busy (the default) or free. */
    val busy: Boolean = true,
)

const val DEFAULT_ALERT_MINUTES = 30

interface CalendarRepository {
    /** Events and dated tasks within [from, to). */
    suspend fun agenda(from: Instant, to: Instant): Result<List<AgendaItem>>
    /** Creates the event (and its alerts, those of [NewEvent.alerts] that are still ahead) and returns the event's id. */
    suspend fun createEvent(event: NewEvent): Result<String>
    suspend fun deleteEvent(id: String): Result<Unit>

    /** Emits after an event was created, so an open calendar can reload. */
    val changes: Flow<Unit>
}
