package com.utkarsh.todo.feature.habits.domain

import java.time.LocalDate

/** A habit with the days it was kept inside the loaded window. */
data class Habit(val id: String, val name: String, val keptDays: Set<LocalDate>)

interface HabitsRepository {
    suspend fun habits(from: LocalDate, to: LocalDate): Result<List<Habit>>
    suspend fun create(name: String): Result<Habit>
    suspend fun setKept(habitId: String, day: LocalDate, kept: Boolean): Result<Unit>
}
