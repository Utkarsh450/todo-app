package com.utkarsh.todo.feature.insights.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.feature.insights.domain.Heatmap
import com.utkarsh.todo.feature.insights.domain.HeatmapMode
import com.utkarsh.todo.feature.insights.domain.InsightsRepository
import com.utkarsh.todo.feature.insights.domain.heatLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val HEATMAP_WEEKS = 18

/** One heat-map square; [level] is 0..4 (index into the colour ramp), null for padding after today. */
data class HeatCellUi(val date: LocalDate, val count: Int, val level: Int, val isSelected: Boolean, val isFuture: Boolean)

data class RhythmBarUi(val letter: String, /** 0f..1f relative to the busiest weekday. */ val fraction: Float, val isPeak: Boolean)

data class InsightsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val mode: HeatmapMode = HeatmapMode.TASKS,
    /** Columns are weeks (oldest first); each holds seven cells Sunday..Saturday. */
    val weeks: List<List<HeatCellUi>> = emptyList(),
    val monthLabels: List<String> = emptyList(),
    val selectedLabel: String = "",
    val selectedValue: String = "",
    val total: Int = 0,
    val streak: Int = 0,
    val best: Int = 0,
    val rhythm: List<RhythmBarUi> = emptyList(),
) {
    val unit: String get() = if (mode == HeatmapMode.TASKS) "Tasks" else "Habits"
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: InsightsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val firstDay: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).minusWeeks((HEATMAP_WEEKS - 1).toLong())
    private var mode = HeatmapMode.TASKS
    private var selected = today
    private var heatmap: Heatmap? = null

    private val _state = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun setMode(newMode: HeatmapMode) {
        if (newMode == mode) return
        mode = newMode
        load()
    }

    fun select(date: LocalDate) {
        if (date.isAfter(today) || date.isBefore(firstDay)) return
        selected = date
        publish()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, mode = mode) }
            val offsetMinutes = clock.zone.rules.getOffset(clock.instant()).totalSeconds / 60
            repository.heatmap(mode, firstDay, today, offsetMinutes)
                .onSuccess { heatmap = it; publish() }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load insights") } }
        }
    }

    private fun publish() {
        val data = heatmap ?: return
        val counts = data.days.associate { it.date to it.count }
        val weeks = (0 until HEATMAP_WEEKS).map { w ->
            (0 until 7).map { d ->
                val date = firstDay.plusDays((w * 7 + d).toLong())
                val count = counts[date] ?: 0
                HeatCellUi(date, count, heatLevel(mode, count), date == selected, isFuture = date.isAfter(today))
            }
        }
        val peak = data.summary.rhythm.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        _state.value = InsightsUiState(
            isLoading = false,
            mode = mode,
            weeks = weeks,
            monthLabels = weeks.map { week -> week.first().date.let { if (it.dayOfMonth <= 7) it.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) else "" } },
            selectedLabel = SELECTED_FORMAT.format(selected),
            selectedValue = "${counts[selected] ?: 0} ${if (mode == HeatmapMode.TASKS) "tasks done" else "habits kept"}",
            total = data.summary.total,
            streak = data.summary.streak,
            best = data.summary.best,
            rhythm = data.summary.rhythm.mapIndexed { i, v ->
                RhythmBarUi("SMTWTFS"[i].toString(), (v / peak).toFloat(), isPeak = v == peak && v > 0)
            },
        )
    }

    private companion object {
        val SELECTED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    }
}
