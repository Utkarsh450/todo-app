package com.utkarsh.todo.feature.notes.domain

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The free-form "Scratchpad" note, one per day. */
interface NotesRepository {
    suspend fun note(day: LocalDate): Result<String>
    suspend fun save(day: LocalDate, text: String): Result<Unit>

    /** Adds [text] on a new line at the end of the day's note, keeping what is already there. */
    suspend fun append(day: LocalDate, text: String): Result<Unit>

    /** Emits after a note was added from outside the scratchpad editor, so an open scratchpad can reload. */
    val changes: Flow<Unit>
}
