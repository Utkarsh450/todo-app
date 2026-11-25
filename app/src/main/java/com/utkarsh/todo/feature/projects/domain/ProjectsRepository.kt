package com.utkarsh.todo.feature.projects.domain

import kotlinx.coroutines.flow.Flow

interface ProjectsRepository {
    /** Emits after a project is created, renamed, recoloured or deleted, so screens showing project names can refresh. */
    val changes: Flow<Unit>

    suspend fun projects(): Result<List<Project>>
    suspend fun create(name: String, colorHex: String): Result<Project>

    /** Changes the name and/or colour; a null argument is left as it is. */
    suspend fun update(id: String, name: String? = null, colorHex: String? = null): Result<Project>

    /** Deletes a project. Its tasks stay, just without a project. */
    suspend fun delete(id: String): Result<Unit>
}
