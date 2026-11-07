package com.utkarsh.todo.feature.projects.data

import com.utkarsh.todo.core.common.resultOf
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import javax.inject.Inject

class ProjectsRepositoryImpl @Inject constructor(private val api: ProjectsApi) : ProjectsRepository {
    override suspend fun projects() = resultOf { api.list().map { it.toDomain() } }

    override suspend fun create(name: String, colorHex: String) =
        resultOf { api.create(CreateProjectRequest(name, colorHex)).toDomain() }
}

internal fun ProjectDto.toDomain() = Project(id = id, name = name, colorHex = color)
