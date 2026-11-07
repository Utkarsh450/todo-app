package com.utkarsh.todo.feature.tasks.data

import com.utkarsh.todo.core.common.resultOf
import com.utkarsh.todo.feature.tasks.domain.Field
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import java.time.Instant
import javax.inject.Inject

class TasksRepositoryImpl @Inject constructor(private val api: TasksApi) : TasksRepository {
    override suspend fun dueBetween(from: Instant, to: Instant) =
        resultOf { api.list(from = from.toString(), to = to.toString()).map { it.toDomain() } }

    override suspend fun search(query: String) = resultOf { api.list(query = query).map { it.toDomain() } }

    override suspend fun get(id: String) = resultOf { api.get(id).toDomain() }

    override suspend fun create(task: NewTask) = resultOf { api.create(task.toRequest()).toDomain() }

    override suspend fun update(id: String, patch: TaskPatch) = resultOf { api.update(id, patch.toJson()).toDomain() }

    override suspend fun setDone(id: String, done: Boolean) =
        update(id, TaskPatch(status = Field.Set(if (done) TaskStatus.DONE else TaskStatus.TODO)))

    override suspend fun delete(id: String) = resultOf { api.delete(id) }

    override suspend fun addSubtask(taskId: String, title: String) =
        resultOf { api.addSubtask(taskId, CreateSubtaskRequest(title)).toDomain() }

    override suspend fun setSubtaskDone(id: String, done: Boolean) =
        resultOf { api.updateSubtask(id, SubtaskPatchRequest(done)).toDomain() }
}
