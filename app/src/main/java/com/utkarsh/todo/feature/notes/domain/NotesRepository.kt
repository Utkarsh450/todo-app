package com.utkarsh.todo.feature.notes.domain

import java.time.LocalDate

/** The free-form "Scratchpad" note, one per day. */
interface NotesRepository {
    suspend fun note(day: LocalDate): Result<String>
    suspend fun save(day: LocalDate, text: String): Result<Unit>
}
