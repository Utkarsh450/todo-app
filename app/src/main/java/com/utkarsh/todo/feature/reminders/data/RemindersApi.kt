package com.utkarsh.todo.feature.reminders.data

import com.utkarsh.todo.core.network.InstantSerializer
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class ReminderDto(
    val id: String,
    val taskId: String? = null,
    val title: String,
    @Serializable(with = InstantSerializer::class) val remindAt: Instant,
    val repeatRule: String? = null,
    val locationTrigger: String? = null,
    val enabled: Boolean = true,
    @Serializable(with = InstantSerializer::class) val snoozedUntil: Instant? = null,
    val eventId: String? = null,
    val style: String = "ALARM",
)

@Serializable
data class GroupedRemindersDto(
    val overdue: List<ReminderDto> = emptyList(),
    val today: List<ReminderDto> = emptyList(),
    val thisWeek: List<ReminderDto> = emptyList(),
    val later: List<ReminderDto> = emptyList(),
)

@Serializable
data class CreateReminderRequest(
    val title: String,
    @Serializable(with = InstantSerializer::class) val remindAt: Instant,
    val taskId: String?,
    val repeatRule: String?,
    val locationTrigger: String?,
    val eventId: String? = null,
    val style: String = "ALARM",
)

@Serializable data class SnoozeMinutesRequest(val minutes: Int)

@Serializable data class SnoozeUntilRequest(@Serializable(with = InstantSerializer::class) val until: Instant)

interface RemindersApi {
    @GET("reminders")
    suspend fun list(): List<ReminderDto>

    @GET("reminders/grouped")
    suspend fun grouped(
        @Query("now") now: String,
        @Query("endOfToday") endOfToday: String,
        @Query("endOfWeek") endOfWeek: String,
    ): GroupedRemindersDto

    @POST("reminders")
    suspend fun create(@Body body: CreateReminderRequest): ReminderDto

    @PATCH("reminders/{id}")
    suspend fun update(@Path("id") id: String, @Body body: JsonObject): ReminderDto

    @POST("reminders/{id}/snooze")
    suspend fun snoozeMinutes(@Path("id") id: String, @Body body: SnoozeMinutesRequest): ReminderDto

    @POST("reminders/{id}/snooze")
    suspend fun snoozeUntil(@Path("id") id: String, @Body body: SnoozeUntilRequest): ReminderDto

    @DELETE("reminders/{id}")
    suspend fun delete(@Path("id") id: String)
}
