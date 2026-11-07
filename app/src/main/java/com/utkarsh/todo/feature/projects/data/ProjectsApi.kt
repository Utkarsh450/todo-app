package com.utkarsh.todo.feature.projects.data

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable
data class ProjectDto(val id: String, val name: String, val color: String)

@Serializable
data class CreateProjectRequest(val name: String, val color: String)

interface ProjectsApi {
    @GET("projects")
    suspend fun list(): List<ProjectDto>

    @POST("projects")
    suspend fun create(@Body body: CreateProjectRequest): ProjectDto
}
