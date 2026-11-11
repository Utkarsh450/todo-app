package com.utkarsh.todo.feature.calendar.data

import com.utkarsh.todo.core.common.resultOf
import com.utkarsh.todo.feature.calendar.domain.AgendaItem
import com.utkarsh.todo.feature.calendar.domain.AgendaKind
import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import com.utkarsh.todo.feature.calendar.domain.NewEvent
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val api: CalendarApi,
    private val reminders: RemindersRepository,
    private val clock: Clock,
) : CalendarRepository {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override suspend fun agenda(from: Instant, to: Instant) =
        resultOf { api.agenda(from.toString(), to.toString()).map { it.toDomain() } }

    override suspend fun createEvent(event: NewEvent) = resultOf {
        api.createEvent(CreateEventRequest(event.title, event.start, event.end, event.allDay, event.projectId, event.location, event.videoLink, event.guests, event.busy)).id
    }.onSuccess { id -> addAlert(id, event) }.also { result -> result.onSuccess { _changes.tryEmit(Unit) } }

    override suspend fun deleteEvent(id: String) = resultOf { api.deleteEvent(id) }.also { result -> result.onSuccess { _changes.tryEmit(Unit) } }

    /** The event is saved by now, so a failing alert must not make creating it look like it failed. */
    private suspend fun addAlert(eventId: String, event: NewEvent) {
        alertTimes(event, clock).forEach { at -> reminders.create(NewReminder(title = event.title, remindAt = at, eventId = eventId, style = event.alertStyle)) }
    }
}

/**
 * When the alerts of [event] should ring: each of [NewEvent.alerts] before the start, or just once at 9:00 that
 * morning for an all-day event. Moments that have already passed are left out.
 */
internal fun alertTimes(event: NewEvent, clock: Clock): List<Instant> {
    if (event.alerts.isEmpty()) return emptyList()
    val times = if (event.allDay) listOf(event.start.atZone(clock.zone).toLocalDate().atTime(9, 0).atZone(clock.zone).toInstant())
    else event.alerts.distinct().sorted().map { event.start.minusSeconds(it * 60L) }
    return times.filter { it.isAfter(clock.instant()) }
}

internal fun AgendaItemDto.toDomain() = AgendaItem(
    kind = when (kind) { "event" -> AgendaKind.EVENT; "reminder" -> AgendaKind.REMINDER; else -> AgendaKind.TASK },
    id = id, title = title, start = startAt, end = endAt, allDay = allDay, projectId = projectId, done = done,
)
