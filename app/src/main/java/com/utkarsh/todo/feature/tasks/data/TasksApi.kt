package com.utkarsh.todo.feature.tasks.data

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
data class SubtaskDto(val id: String, val taskId: String, val title: String, val done: Boolean)

@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val notes: String = "",
    val projectId: String? = null,
    val priority: String,
    val status: String,
    @Serializable(with = InstantSerializer::class) val dueAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val completedAt: Instant? = null,
    val archived: Boolean = false,
    val pinned: Boolean = false,
    val repeatRule: String? = null,
    val subtasks: List<SubtaskDto> = emptyList(),
)

@Serializable
data class CreateTaskRequest(
    val title: String,
    val notes: String,
    val projectId: String?,
    val priority: String,
    @Serializable(with = InstantSerializer::class) val dueAt: Instant?,
    val repeatRule: String?,
    val subtasks: List<String>,
)

@Serializable
data class CreateSubtaskRequest(val title: String)

@Serializable
data class SubtaskPatchRequest(val done: Boolean)

interface TasksApi {
    @GET("tasks")
    suspend fun list(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("q") query: String? = null,
    ): List<TaskDto>

    @GET("tasks/{id}")
    suspend fun get(@Path("id") id: String): TaskDto

    @POST("tasks")
    suspend fun create(@Body body: CreateTaskRequest): TaskDto

    /** Body is a hand-built object so unset fields are omitted while explicit nulls are kept. */
    @PATCH("tasks/{id}")
    suspend fun update(@Path("id") id: String, @Body body: JsonObject): TaskDto

    @DELETE("tasks/{id}")
    suspend fun delete(@Path("id") id: String)

    @POST("tasks/{id}/subtasks")
    suspend fun addSubtask(@Path("id") taskId: String, @Body body: CreateSubtaskRequest): SubtaskDto

    @PATCH("subtasks/{id}")
    suspend fun updateSubtask(@Path("id") id: String, @Body body: SubtaskPatchRequest): SubtaskDto
}
