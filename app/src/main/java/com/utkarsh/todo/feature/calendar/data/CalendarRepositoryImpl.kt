package com.utkarsh.todo.feature.calendar.data

import com.utkarsh.todo.core.common.resultOf
import com.utkarsh.todo.feature.calendar.domain.AgendaItem
import com.utkarsh.todo.feature.calendar.domain.AgendaKind
import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import com.utkarsh.todo.feature.calendar.domain.NewEvent
import java.time.Instant
import javax.inject.Inject

class CalendarRepositoryImpl @Inject constructor(private val api: CalendarApi) : CalendarRepository {
    override suspend fun agenda(from: Instant, to: Instant) =
        resultOf { api.agenda(from.toString(), to.toString()).map { it.toDomain() } }

    override suspend fun createEvent(event: NewEvent) = resultOf {
        api.createEvent(CreateEventRequest(event.title, event.start, event.end, event.allDay, event.projectId))
        Unit
    }
}

internal fun AgendaItemDto.toDomain() = AgendaItem(
    kind = if (kind == "event") AgendaKind.EVENT else AgendaKind.TASK,
    id = id, title = title, start = startAt, end = endAt, allDay = allDay, projectId = projectId, done = done,
)
