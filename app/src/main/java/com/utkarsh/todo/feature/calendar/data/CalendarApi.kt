package com.utkarsh.todo.feature.calendar.data

import com.utkarsh.todo.core.network.InstantSerializer
import java.time.Instant
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class AgendaItemDto(
    val kind: String,
    val id: String,
    val title: String,
    @Serializable(with = InstantSerializer::class) val startAt: Instant,
    @Serializable(with = InstantSerializer::class) val endAt: Instant? = null,
    val allDay: Boolean = false,
    val projectId: String? = null,
    val done: Boolean = false,
)

@Serializable
data class CreateEventRequest(
    val title: String,
    @Serializable(with = InstantSerializer::class) val startAt: Instant,
    @Serializable(with = InstantSerializer::class) val endAt: Instant,
    val allDay: Boolean,
    val projectId: String?,
)

@Serializable
data class EventDto(val id: String, val title: String)

interface CalendarApi {
    @GET("calendar/agenda")
    suspend fun agenda(@Query("from") from: String, @Query("to") to: String): List<AgendaItemDto>

    @POST("events")
    suspend fun createEvent(@Body body: CreateEventRequest): EventDto

    @DELETE("events/{id}")
    suspend fun deleteEvent(@Path("id") id: String)
}
