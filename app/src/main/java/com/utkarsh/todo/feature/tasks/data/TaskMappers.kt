package com.utkarsh.todo.feature.tasks.data

import com.utkarsh.todo.feature.tasks.domain.Field
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Subtask
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

internal fun SubtaskDto.toDomain() = Subtask(id = id, taskId = taskId, title = title, done = done)

internal fun TaskDto.toDomain() = Task(
    id = id,
    title = title,
    notes = notes,
    projectId = projectId,
    priority = Priority.valueOf(priority),
    status = TaskStatus.valueOf(status),
    dueAt = dueAt,
    completedAt = completedAt,
    archived = archived,
    pinned = pinned,
    repeatRule = repeatRule,
    subtasks = subtasks.map { it.toDomain() },
)

internal fun NewTask.toRequest() = CreateTaskRequest(
    title = title,
    notes = notes,
    projectId = projectId,
    priority = priority.name,
    dueAt = dueAt,
    repeatRule = repeatRule,
    subtasks = subtasks,
)

private fun nullable(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

/** Serialises only the fields the caller set, so `Field.Set(null)` sends an explicit `null`. */
internal fun TaskPatch.toJson(): JsonObject = buildJsonObject {
    fun <T> set(key: String, field: Field<T>, encode: (T) -> JsonElement) {
        if (field is Field.Set) put(key, encode(field.value))
    }
    set("title", title) { JsonPrimitive(it) }
    set("notes", notes) { JsonPrimitive(it) }
    set("projectId", projectId) { nullable(it) }
    set("priority", priority) { JsonPrimitive(it.name) }
    set("status", status) { JsonPrimitive(it.name) }
    set("dueAt", dueAt) { nullable(it?.toString()) }
    set("archived", archived) { JsonPrimitive(it) }
    set("pinned", pinned) { JsonPrimitive(it) }
    set("repeatRule", repeatRule) { nullable(it) }
}
