package com.utkarsh.todo.feature.ai.domain

import com.utkarsh.todo.feature.tasks.domain.Priority
import java.time.Instant
import java.time.ZoneId

/** Why an AI request failed, in words that are safe to show. [notConfigured] means the server has no API key yet. */
class AiException(message: String, val notConfigured: Boolean = false, val notFound: Boolean = false) : Exception(message)

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

/** A fact the assistant keeps about the owner ("Wakes at 7"). [category] is ROUTINE, PREFERENCE, PERSON, COMMITMENT or OTHER. */
data class MemoryFact(val id: String, val text: String, val category: String)

/**
 * The assistant's answer. [messageId] is the saved message (null from a server that predates memory) and outcomes of
 * its suggestions are reported against it. [noted] are the facts it saved by itself in this turn, [forgotten] the ids it dropped.
 * [suggestionIndexes] holds each suggestion's position in the server's list: the app drops suggestions it cannot read,
 * so the positions can skip, and an outcome must be reported with the server's own index.
 */
data class AssistantReply(
    val reply: String,
    val suggestions: List<AssistantSuggestion>,
    val messageId: String? = null,
    val noted: List<MemoryFact> = emptyList(),
    val forgotten: List<String> = emptyList(),
    val suggestionIndexes: List<Int> = suggestions.indices.toList(),
)

/** One saved message of the conversation. [outcomes] maps the server's suggestion index to "accepted" or "skipped". */
data class StoredMessage(
    val id: String,
    val fromOwner: Boolean,
    val text: String,
    val suggestions: List<AssistantSuggestion> = emptyList(),
    val suggestionIndexes: List<Int> = suggestions.indices.toList(),
    val outcomes: Map<Int, String> = emptyMap(),
)

/** A page of history, newest message first. [nextBefore] is the cursor for the next (older) page, null at the end. */
data class HistoryPage(val messages: List<StoredMessage>, val nextBefore: String?)

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

    /** One page of the saved conversation, newest first. Pass the previous page's `nextBefore` as [before] for older ones. */
    suspend fun history(before: String? = null, limit: Int = 20): Result<HistoryPage>

    /** Deletes the whole conversation (what the assistant remembers about the owner is kept). */
    suspend fun clearHistory(): Result<Unit>

    /** Tells the server what the owner did with suggestion [index] (the server's position) of a message. */
    suspend fun reportOutcome(messageId: String, index: Int, accepted: Boolean): Result<Unit>

    suspend fun memories(): Result<List<MemoryFact>>

    /** Adds a fact by hand. Fails with the server's reason when it is refused (too long, a secret, memory full). */
    suspend fun addMemory(text: String): Result<MemoryFact>
    suspend fun updateMemory(id: String, text: String): Result<MemoryFact>
    suspend fun deleteMemory(id: String): Result<Unit>
    suspend fun deleteAllMemories(): Result<Unit>
}
