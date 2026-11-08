package com.utkarsh.todo.feature.ai.domain

import com.utkarsh.todo.feature.tasks.domain.Priority
import java.time.Instant
import java.time.ZoneId

/** Why an AI request failed, in words that are safe to show. [notConfigured] means the server has no API key yet. */
class AiException(message: String, val notConfigured: Boolean = false) : Exception(message)

/** One change the assistant proposes. Nothing happens until the person accepts it. */
sealed interface AssistantSuggestion {
    val reason: String

    data class Move(val taskId: String, val taskTitle: String, override val reason: String, val dueAt: Instant) : AssistantSuggestion
    data class Split(val taskId: String, val taskTitle: String, override val reason: String, val steps: List<String>) : AssistantSuggestion
    data class ChangePriority(val taskId: String, val taskTitle: String, override val reason: String, val priority: Priority) : AssistantSuggestion
    data class Complete(val taskId: String, val taskTitle: String, override val reason: String) : AssistantSuggestion

    data class CreateTask(
        val title: String,
        override val reason: String,
        val dueAt: Instant?,
        val priority: Priority,
        val repeatRule: String?,
        val projectId: String?,
        val projectName: String?,
    ) : AssistantSuggestion

    data class CreateEvent(val title: String, override val reason: String, val startAt: Instant, val endAt: Instant, val allDay: Boolean) : AssistantSuggestion
    data class CreateReminder(val title: String, override val reason: String, val remindAt: Instant, val repeatRule: String?) : AssistantSuggestion
}

/** A task found in a rambling note. Nothing is created until the person confirms. */
data class DumpedTask(
    val title: String,
    val dueAt: Instant?,
    val priority: Priority,
    val repeatRule: String?,
    val projectId: String?,
    val projectName: String?,
)

data class AssistantReply(val reply: String, val suggestions: List<AssistantSuggestion>)

interface AiRepository {
    /**
     * Asks the assistant to split a task into steps. Nothing is saved; the caller decides which steps to add.
     * Fails with an [AiException] carrying a readable message.
     */
    suspend fun breakDown(title: String, notes: String, dueAt: Instant?): Result<List<String>>

    /** Asks one question about the open tasks. [zone] makes times in the answer match the person's day. */
    suspend fun assist(message: String, zone: ZoneId): Result<AssistantReply>

    /** Sorts a rambling note into separate tasks. Returns an empty list when the note holds none. */
    suspend fun brainDump(text: String, zone: ZoneId): Result<List<DumpedTask>>
}
