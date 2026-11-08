package com.utkarsh.todo.feature.tasks.domain

import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface TasksRepository {
    /** Emits after any successful write so screens showing tasks can reload. */
    val changes: Flow<Unit>

    /** Non-archived tasks due in [from, to), ordered by due time. */
    suspend fun dueBetween(from: Instant, to: Instant): Result<List<Task>>
    suspend fun search(query: String): Result<List<Task>>

    /** Every non-archived task, done or not. */
    suspend fun all(): Result<List<Task>>
    suspend fun get(id: String): Result<Task>
    suspend fun create(task: NewTask): Result<Task>
    suspend fun update(id: String, patch: TaskPatch): Result<Task>
    suspend fun setDone(id: String, done: Boolean): Result<Task>
    suspend fun delete(id: String): Result<Unit>
    suspend fun addSubtask(taskId: String, title: String): Result<Subtask>
    suspend fun setSubtaskDone(id: String, done: Boolean): Result<Subtask>
}
