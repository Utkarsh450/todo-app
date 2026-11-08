package com.utkarsh.todo.feature.ai.domain

import java.time.Instant

/** Why an AI request failed, in words that are safe to show. [notConfigured] means the server has no API key yet. */
class AiException(message: String, val notConfigured: Boolean = false) : Exception(message)

interface AiRepository {
    /**
     * Asks the assistant to split a task into steps. Nothing is saved; the caller decides which steps to add.
     * Fails with an [AiException] carrying a readable message.
     */
    suspend fun breakDown(title: String, notes: String, dueAt: Instant?): Result<List<String>>
}
