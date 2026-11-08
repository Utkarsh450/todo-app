package com.utkarsh.todo.feature.calendar.domain

import java.time.Instant

enum class AgendaKind { EVENT, TASK }

/** One entry on the calendar: a scheduled event or a task with a due time. */
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
)

interface CalendarRepository {
    /** Events and dated tasks within [from, to). */
    suspend fun agenda(from: Instant, to: Instant): Result<List<AgendaItem>>
    suspend fun createEvent(event: NewEvent): Result<Unit>
}
