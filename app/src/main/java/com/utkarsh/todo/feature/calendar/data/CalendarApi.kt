package com.utkarsh.todo.feature.calendar.data

import com.utkarsh.todo.core.network.InstantSerializer
import java.time.Instant
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
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
    val location: String? = null,
    val videoLink: String? = null,
    val guests: List<String> = emptyList(),
    val busy: Boolean = true,
)

@Serializable
data class EventDto(val id: String, val title: String)

/** One event with everything the Create screen edits. */
@Serializable
data class EventDetailDto(
    val id: String,
    val title: String,
    @Serializable(with = InstantSerializer::class) val startAt: Instant,
    @Serializable(with = InstantSerializer::class) val endAt: Instant,
    val allDay: Boolean = false,
    val projectId: String? = null,
    val location: String? = null,
    val videoLink: String? = null,
    val guests: List<String> = emptyList(),
    val busy: Boolean = true,
)

interface CalendarApi {
    @GET("calendar/agenda")
    suspend fun agenda(@Query("from") from: String, @Query("to") to: String): List<AgendaItemDto>

    @POST("events")
    suspend fun createEvent(@Body body: CreateEventRequest): EventDto

    @GET("events/{id}")
    suspend fun event(@Path("id") id: String): EventDetailDto

    @PATCH("events/{id}")
    suspend fun updateEvent(@Path("id") id: String, @Body body: CreateEventRequest): EventDto

    @DELETE("events/{id}")
    suspend fun deleteEvent(@Path("id") id: String)
}
