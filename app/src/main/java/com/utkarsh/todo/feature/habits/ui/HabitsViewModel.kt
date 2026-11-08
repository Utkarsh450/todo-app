package com.utkarsh.todo.feature.habits.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.feature.calendar.domain.MonthGrid
import com.utkarsh.todo.feature.habits.domain.Habit
import com.utkarsh.todo.feature.habits.domain.HabitsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HabitRowUi(val id: String, val name: String, val kept: List<Boolean>, val keptCount: Int)

data class HabitsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Seven columns Sunday..Saturday of the current week; [todayIndex] is highlighted. */
    val dayLetters: List<String> = listOf("S", "M", "T", "W", "T", "F", "S"),
    val todayIndex: Int = 0,
    val rows: List<HabitRowUi> = emptyList(),
) {
    val keptToday: Int get() = rows.count { it.kept.getOrNull(todayIndex) == true }
    val summary: String get() = "$keptToday/${rows.size} today"
}

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val repository: HabitsRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val week = MonthGrid.week(today)

    private var habits: List<Habit> = emptyList()
    private val _state = MutableStateFlow(HabitsUiState(todayIndex = week.indexOf(today)))
    val uiState: StateFlow<HabitsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.habits(week.first(), week.last())
                .onSuccess { habits = it; publish() }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load habits") } }
        }
    }

    /** Toggles one day of a habit optimistically; a failure restores the server state. */
    fun toggle(habitId: String, dayIndex: Int) {
        val day = week.getOrNull(dayIndex) ?: return
        val habit = habits.firstOrNull { it.id == habitId } ?: return
        val kept = day !in habit.keptDays
        habits = habits.map { if (it.id == habitId) it.copy(keptDays = if (kept) it.keptDays + day else it.keptDays - day) else it }
        publish()
        viewModelScope.launch {
            repository.setKept(habitId, day, kept).onFailure {
                _state.update { s -> s.copy(error = "Couldn't update habit") }
                refresh()
            }
        }
    }

    fun add(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.create(clean)
                .onSuccess { habits = habits + it; publish() }
                .onFailure { _state.update { s -> s.copy(error = "Couldn't add habit") } }
        }
    }

    private fun publish() = _state.update { state ->
        state.copy(
            isLoading = false,
            error = null,
            rows = habits.map { h ->
                val kept = week.map { it in h.keptDays }
                HabitRowUi(h.id, h.name, kept, kept.count { it })
            },
        )
    }
}
