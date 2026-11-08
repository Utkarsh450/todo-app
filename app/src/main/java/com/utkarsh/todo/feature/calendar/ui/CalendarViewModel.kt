package com.utkarsh.todo.feature.calendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.core.common.formatTime
import com.utkarsh.todo.feature.calendar.domain.AgendaItem
import com.utkarsh.todo.feature.calendar.domain.AgendaKind
import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import com.utkarsh.todo.feature.calendar.domain.MonthGrid
import com.utkarsh.todo.feature.calendar.domain.TimelineLayout
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CalendarMode { MONTH, WEEK }

data class DayCellUi(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    /** Up to three project colours (`#RRGGBB`); null stands for "no project". */
    val dots: List<String?>,
)

data class WeekDayUi(val date: LocalDate, val letter: String, val isSelected: Boolean, val isToday: Boolean, val hasItems: Boolean)

data class AgendaRowUi(
    val id: String,
    val title: String,
    val timeLabel: String,
    val projectName: String?,
    val projectColorHex: String?,
    val done: Boolean,
    val isEvent: Boolean,
)

data class TimelineBlockUi(
    val id: String,
    val title: String,
    val timeLabel: String,
    val colorHex: String?,
    val startMinute: Int, // minutes since [TimelineUi.startHour]
    val durationMinutes: Int,
    val lane: Int,
    val laneCount: Int,
)

data class TimelineUi(val startHour: Int, val endHour: Int, val blocks: List<TimelineBlockUi>, val allDay: List<AgendaRowUi>)

data class CalendarUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val mode: CalendarMode = CalendarMode.MONTH,
    val monthLabel: String = "",
    val yearLabel: String = "",
    val cells: List<DayCellUi> = emptyList(),
    val weekDays: List<WeekDayUi> = emptyList(),
    val selectedLabel: String = "",
    val plannedLabel: String = "",
    val agenda: List<AgendaRowUi> = emptyList(),
    val timeline: TimelineUi = TimelineUi(DEFAULT_START_HOUR, DEFAULT_END_HOUR, emptyList(), emptyList()),
)

internal const val DEFAULT_START_HOUR = 9
internal const val DEFAULT_END_HOUR = 18

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendar: CalendarRepository,
    private val projects: ProjectsRepository,
    private val tasks: TasksRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private var month = YearMonth.from(today)
    private var selected = today
    private var mode = CalendarMode.MONTH
    private var itemsByDay: Map<LocalDate, List<AgendaItem>> = emptyMap()
    private var projectsById: Map<String, Project> = emptyMap()

    private val _state = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { tasks.changes.collect { load(showSpinner = false) } }
    }

    fun previousMonth() = show(month.minusMonths(1))
    fun nextMonth() = show(month.plusMonths(1))
    fun goToToday() = show(YearMonth.from(today), select = today)

    fun select(date: LocalDate) {
        if (YearMonth.from(date) == month) {
            selected = date
            publish()
        } else {
            show(YearMonth.from(date), select = date)
        }
    }

    fun setMode(newMode: CalendarMode) {
        mode = newMode
        publish()
    }

    fun refresh() = load()

    /** Switches the visible month; by default selects today if it is in that month, else the 1st. */
    private fun show(target: YearMonth, select: LocalDate? = null) {
        month = target
        selected = select ?: if (target == YearMonth.from(today)) today else target.atDay(1)
        load()
    }

    private fun load(showSpinner: Boolean = true) {
        viewModelScope.launch {
            if (showSpinner) _state.update { it.copy(isLoading = true, error = null) }
            val grid = MonthGrid.dates(month)
            val from = grid.first().atStartOfDay(clock.zone).toInstant()
            val to = grid.last().plusDays(1).atStartOfDay(clock.zone).toInstant()
            val projectsCall = async { projects.projects() }
            val agendaResult = calendar.agenda(from, to)
            projectsById = projectsCall.await().getOrDefault(emptyList()).associateBy { it.id }
            agendaResult
                .onSuccess { items ->
                    itemsByDay = items.groupBy { it.start.atZone(clock.zone).toLocalDate() }
                    publish()
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load calendar") } }
        }
    }

    private fun publish() {
        val dayItems = itemsByDay[selected].orEmpty()
        _state.value = CalendarUiState(
            isLoading = false,
            mode = mode,
            monthLabel = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            yearLabel = month.year.toString(),
            cells = MonthGrid.dates(month).map(::cell),
            weekDays = MonthGrid.week(selected).map { d ->
                WeekDayUi(d, d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), d == selected, d == today, itemsByDay[d].orEmpty().isNotEmpty())
            },
            selectedLabel = SELECTED_FORMAT.format(selected),
            plannedLabel = if (dayItems.isEmpty()) "" else "${dayItems.size} planned",
            agenda = dayItems.sortedBy { it.start }.map(::row),
            timeline = timeline(dayItems),
        )
    }

    private fun cell(date: LocalDate) = DayCellUi(
        date = date,
        inMonth = YearMonth.from(date) == month,
        isToday = date == today,
        isSelected = date == selected,
        dots = itemsByDay[date].orEmpty().map { it.projectId?.let(projectsById::get)?.colorHex }.distinct().take(3),
    )

    private fun row(item: AgendaItem): AgendaRowUi {
        val project = item.projectId?.let(projectsById::get)
        return AgendaRowUi(
            id = item.id,
            title = item.title,
            timeLabel = if (item.allDay) "All day" else item.start.formatTime(clock.zone),
            projectName = project?.name,
            projectColorHex = project?.colorHex,
            done = item.done,
            isEvent = item.kind == AgendaKind.EVENT,
        )
    }

    private fun timeline(items: List<AgendaItem>): TimelineUi {
        val timed = items.filterNot { it.allDay }
        val placed = TimelineLayout.place(timed, { it.start }, { it.end })
        val startHour = minOf(DEFAULT_START_HOUR, placed.minOfOrNull { it.start.atZone(clock.zone).hour } ?: DEFAULT_START_HOUR)
        val endHour = maxOf(
            DEFAULT_END_HOUR,
            placed.maxOfOrNull { block -> block.end.atZone(clock.zone).let { it.hour + if (it.minute > 0) 1 else 0 } } ?: DEFAULT_END_HOUR,
        ).coerceAtMost(24)
        val origin = selected.atTime(startHour, 0).atZone(clock.zone).toInstant()
        return TimelineUi(
            startHour = startHour,
            endHour = endHour,
            blocks = placed.map { p ->
                TimelineBlockUi(
                    id = p.item.id,
                    title = p.item.title,
                    timeLabel = p.item.start.formatTime(clock.zone),
                    colorHex = p.item.projectId?.let(projectsById::get)?.colorHex,
                    startMinute = Duration.between(origin, p.start).toMinutes().toInt().coerceAtLeast(0),
                    durationMinutes = Duration.between(p.start, p.end).toMinutes().toInt(),
                    lane = p.lane,
                    laneCount = p.laneCount,
                )
            },
            allDay = items.filter { it.allDay }.map(::row),
        )
    }

    private companion object {
        val SELECTED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMMM", Locale.ENGLISH)
    }
}
