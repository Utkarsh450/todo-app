package com.utkarsh.todo.feature.habits.data

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class HabitDto(val id: String, val name: String, val days: List<String> = emptyList())

@Serializable data class CreateHabitRequest(val name: String)

@Serializable data class HabitLogRequest(val done: Boolean)

@Serializable data class HabitLogResponse(val habitId: String, val date: String, val done: Boolean)

interface HabitsApi {
    @GET("habits")
    suspend fun list(@Query("from") from: String, @Query("to") to: String): List<HabitDto>

    @POST("habits")
    suspend fun create(@Body body: CreateHabitRequest): HabitDto

    @PUT("habits/{id}/logs/{date}")
    suspend fun setLog(@Path("id") id: String, @Path("date") date: String, @Body body: HabitLogRequest): HabitLogResponse
}
