package com.utkarsh.todo.feature.projects.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class ProjectDto(val id: String, val name: String, val color: String)

@Serializable
data class CreateProjectRequest(val name: String, val color: String)

interface ProjectsApi {
    @GET("projects")
    suspend fun list(): List<ProjectDto>

    @POST("projects")
    suspend fun create(@Body body: CreateProjectRequest): ProjectDto

    /** Hand-built body so only the fields being changed are sent. */
    @PATCH("projects/{id}")
    suspend fun update(@Path("id") id: String, @Body body: JsonObject): ProjectDto

    @DELETE("projects/{id}")
    suspend fun delete(@Path("id") id: String)
}
