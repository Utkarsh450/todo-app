package com.utkarsh.todo.feature.projects.data

import com.utkarsh.todo.core.common.resultOf
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** A singleton, so [changes] reaches every screen (the same as the tasks and calendar repositories). */
@Singleton
class ProjectsRepositoryImpl @Inject constructor(private val api: ProjectsApi) : ProjectsRepository {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    private fun <T> Result<T>.notifying(): Result<T> = onSuccess { _changes.tryEmit(Unit) }

    override suspend fun projects() = resultOf { api.list().map { it.toDomain() } }

    override suspend fun create(name: String, colorHex: String) =
        resultOf { api.create(CreateProjectRequest(name, colorHex)).toDomain() }.notifying()

    override suspend fun update(id: String, name: String?, colorHex: String?) = resultOf {
        api.update(id, buildJsonObject {
            name?.let { put("name", JsonPrimitive(it)) }
            colorHex?.let { put("color", JsonPrimitive(it)) }
        }).toDomain()
    }.notifying()

    override suspend fun delete(id: String) = resultOf { api.delete(id) }.notifying()
}

internal fun ProjectDto.toDomain() = Project(id = id, name = name, colorHex = color)
