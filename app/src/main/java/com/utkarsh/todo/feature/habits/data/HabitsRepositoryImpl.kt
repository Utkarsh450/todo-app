package com.utkarsh.todo.feature.habits.data

import com.utkarsh.todo.core.common.resultOf
import com.utkarsh.todo.feature.habits.domain.Habit
import com.utkarsh.todo.feature.habits.domain.HabitsRepository
import java.time.LocalDate
import javax.inject.Inject

class HabitsRepositoryImpl @Inject constructor(private val api: HabitsApi) : HabitsRepository {
    override suspend fun habits(from: LocalDate, to: LocalDate) =
        resultOf { api.list(from.toString(), to.toString()).map { it.toDomain() } }

    override suspend fun create(name: String) = resultOf { api.create(CreateHabitRequest(name)).toDomain() }

    override suspend fun setKept(habitId: String, day: LocalDate, kept: Boolean) =
        resultOf { api.setLog(habitId, day.toString(), HabitLogRequest(kept)); Unit }
}

internal fun HabitDto.toDomain() = Habit(id, name, days.map(LocalDate::parse).toSet())
