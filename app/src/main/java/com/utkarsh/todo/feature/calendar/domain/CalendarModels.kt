package com.utkarsh.todo.feature.calendar.domain

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
    /** Minutes before the start that a reminder should ring (an alarm like Google Calendar's), or null for none. */
    val alertMinutes: Int? = DEFAULT_ALERT_MINUTES,
)

const val DEFAULT_ALERT_MINUTES = 30

interface CalendarRepository {
    /** Events and dated tasks within [from, to). */
    suspend fun agenda(from: Instant, to: Instant): Result<List<AgendaItem>>
    /** Creates the event (and its alert, if [NewEvent.alertMinutes] is set and still ahead) and returns the event's id. */
    suspend fun createEvent(event: NewEvent): Result<String>
    suspend fun deleteEvent(id: String): Result<Unit>

    /** Emits after an event was created, so an open calendar can reload. */
    val changes: Flow<Unit>
}
