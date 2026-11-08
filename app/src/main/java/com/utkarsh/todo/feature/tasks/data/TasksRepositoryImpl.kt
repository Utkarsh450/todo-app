package com.utkarsh.todo.feature.tasks.data

import com.utkarsh.todo.core.common.resultOf
import com.utkarsh.todo.feature.tasks.domain.Field
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class TasksRepositoryImpl @Inject constructor(private val api: TasksApi) : TasksRepository {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    private fun <T> Result<T>.notifying(): Result<T> = onSuccess { _changes.tryEmit(Unit) }

    override suspend fun dueBetween(from: Instant, to: Instant) =
        resultOf { api.list(from = from.toString(), to = to.toString()).map { it.toDomain() } }

    override suspend fun search(query: String) = resultOf { api.list(query = query).map { it.toDomain() } }

    override suspend fun all() = resultOf { api.list().map { it.toDomain() } }

    override suspend fun get(id: String) = resultOf { api.get(id).toDomain() }

    override suspend fun create(task: NewTask) = resultOf { api.create(task.toRequest()).toDomain() }.notifying()

    override suspend fun update(id: String, patch: TaskPatch) = resultOf { api.update(id, patch.toJson()).toDomain() }.notifying()

    override suspend fun setDone(id: String, done: Boolean) =
        update(id, TaskPatch(status = Field.Set(if (done) TaskStatus.DONE else TaskStatus.TODO)))

    override suspend fun delete(id: String) = resultOf { api.delete(id) }.notifying()

    override suspend fun addSubtask(taskId: String, title: String) =
        resultOf { api.addSubtask(taskId, CreateSubtaskRequest(title)).toDomain() }.notifying()

    override suspend fun setSubtaskDone(id: String, done: Boolean) =
        resultOf { api.updateSubtask(id, SubtaskPatchRequest(done)).toDomain() }.notifying()
}
