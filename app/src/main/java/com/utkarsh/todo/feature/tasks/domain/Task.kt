package com.utkarsh.todo.feature.tasks.domain

import java.time.Instant

enum class Priority { URGENT, HIGH, NORMAL, LOW }

enum class TaskStatus { TODO, IN_PROGRESS, DONE }

data class Subtask(val id: String, val taskId: String, val title: String, val done: Boolean)

data class Task(
    val id: String,
    val title: String,
    val notes: String,
    val projectId: String?,
    val priority: Priority,
    val status: TaskStatus,
    val dueAt: Instant?,
    val completedAt: Instant?,
    val archived: Boolean,
    val pinned: Boolean,
    val repeatRule: String?,
    val subtasks: List<Subtask>,
) {
    val isDone: Boolean get() = status == TaskStatus.DONE
    val subtasksDone: Int get() = subtasks.count { it.done }
}

/** Values needed to create a task. */
data class NewTask(
    val title: String,
    val notes: String = "",
    val projectId: String? = null,
    val priority: Priority = Priority.NORMAL,
    val dueAt: Instant? = null,
    val repeatRule: String? = null,
    val subtasks: List<String> = emptyList(),
)

/** A field in a partial update: either left untouched or set to a (possibly null) value. */
sealed interface Field<out T> {
    data object Unset : Field<Nothing>
    data class Set<T>(val value: T) : Field<T>
}

/** Partial task update; only [Field.Set] fields are sent, so `Set(null)` clears a value. */
data class TaskPatch(
    val title: Field<String> = Field.Unset,
    val notes: Field<String> = Field.Unset,
    val projectId: Field<String?> = Field.Unset,
    val priority: Field<Priority> = Field.Unset,
    val status: Field<TaskStatus> = Field.Unset,
    val dueAt: Field<Instant?> = Field.Unset,
    val archived: Field<Boolean> = Field.Unset,
    val pinned: Field<Boolean> = Field.Unset,
    val repeatRule: Field<String?> = Field.Unset,
)
