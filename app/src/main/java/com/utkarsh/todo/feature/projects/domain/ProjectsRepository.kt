package com.utkarsh.todo.feature.projects.domain

interface ProjectsRepository {
    suspend fun projects(): Result<List<Project>>
    suspend fun create(name: String, colorHex: String): Result<Project>
}
