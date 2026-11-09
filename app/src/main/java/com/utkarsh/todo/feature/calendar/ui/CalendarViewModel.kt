package com.utkarsh.todo.feature.calendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.core.common.formatTime
import com.utkarsh.todo.feature.calendar.domain.AgendaItem
import com.utkarsh.todo.feature.calendar.domain.AgendaKind
import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import com.utkarsh.todo.feature.calendar.domain.CreateDraft
import com.utkarsh.todo.feature.calendar.domain.CreateKind
import com.utkarsh.todo.feature.calendar.domain.MonthGrid
import com.utkarsh.todo.feature.calendar.domain.NewEvent
import com.utkarsh.todo.feature.calendar.domain.TimelineLayout
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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

/** The ways to look at the calendar, like Google Calendar's menu. */
enum class CalendarMode(val label: String) { SCHEDULE("Schedule"), DAY("Day"), WEEK("Week"), MONTH("Month") }

/** A one-line bar in a month cell. */
data class ChipUi(val id: String, val title: String, val colorHex: String?, val kind: AgendaKind, val done: Boolean)

data class DayCellUi(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    /** Up to three bars with the day's first entries; [more] counts the rest. */
    val chips: List<ChipUi>,
    val more: Int,
)

data class AgendaRowUi(
    val id: String,
    val title: String,
    val timeLabel: String,
    val projectName: String?,
    val projectColorHex: String?,
    val done: Boolean,
    val kind: AgendaKind,
) {
    val isEvent: Boolean get() = kind == AgendaKind.EVENT
}

data class TimelineBlockUi(
    val id: String,
    val title: String,
    val timeLabel: String,
    val colorHex: String?,
    val kind: AgendaKind,
    val done: Boolean,
    val startMinute: Int, // minutes since midnight
    val durationMinutes: Int,
    val lane: Int,
    val laneCount: Int,
)

/** One day of the week or day view: its header, its all-day strip and its timed blocks. */
data class DayColumnUi(
    val date: LocalDate,
    val letter: String,
    val isToday: Boolean,
    val isSelected: Boolean,
    val allDay: List<AgendaRowUi>,
    val blocks: List<TimelineBlockUi>,
)

data class ScheduleDayUi(val date: LocalDate, val label: String, val isToday: Boolean, val rows: List<AgendaRowUi>)

enum class CreateStatus { EDITING, SAVING }

data class CreateSheetUi(val draft: CreateDraft, val status: CreateStatus = CreateStatus.EDITING, val error: String? = null) {
    val saving: Boolean get() = status == CreateStatus.SAVING
}

/** An event or reminder opened from the calendar, with what can be done to it. */
data class DetailUi(val id: String, val kind: AgendaKind, val title: String, val whenLabel: String, val deleting: Boolean = false, val error: String? = null)

data class CalendarUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val mode: CalendarMode = CalendarMode.MONTH,
    val monthLabel: String = "",
    val yearLabel: String = "",
    val cells: List<DayCellUi> = emptyList(),
    val columns: List<DayColumnUi> = emptyList(),
    val selectedLabel: String = "",
    val plannedLabel: String = "",
    val agenda: List<AgendaRowUi> = emptyList(),
    val schedule: List<ScheduleDayUi> = emptyList(),
    /** Minutes since midnight of "now" when today is on screen, for the red now-line; null otherwise. */
    val nowMinute: Int? = null,
    /** The hour the timeline should scroll to when it opens (just before the first entry, or the morning). */
    val scrollHour: Int = DEFAULT_SCROLL_HOUR,
    val create: CreateSheetUi? = null,
    val detail: DetailUi? = null,
)

internal const val DEFAULT_SCROLL_HOUR = 7
internal const val SCHEDULE_DAYS = 31L
private const val MAX_CHIPS = 3

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendar: CalendarRepository,
    private val projects: ProjectsRepository,
    private val tasks: TasksRepository,
    private val reminders: RemindersRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private var selected = today
    private var mode = CalendarMode.MONTH
    private var itemsByDay: Map<LocalDate, List<AgendaItem>> = emptyMap()
    private var projectsById: Map<String, Project> = emptyMap()
    private var loadedFrom: LocalDate? = null
    private var loadedTo: LocalDate? = null
    private var create: CreateSheetUi? = null
    private var detail: DetailUi? = null

    private val _state = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { tasks.changes.collect { load(showSkeleton = false) } }
        viewModelScope.launch { calendar.changes.collect { load(showSkeleton = false) } }
    }

    // ---- moving around

    /** One month, week or day back, depending on the view (Schedule moves a week). */
    fun previous() = move(-1)
    fun next() = move(1)

    private fun move(direction: Long) = goTo(
        when (mode) {
            CalendarMode.MONTH -> selected.plusMonths(direction)
            CalendarMode.WEEK, CalendarMode.SCHEDULE -> selected.plusWeeks(direction)
            CalendarMode.DAY -> selected.plusDays(direction)
        },
    )

    fun goToToday() = goTo(today)

    fun select(date: LocalDate) = goTo(date)

    /** Tapping a day's number in the week header opens that day. */
    fun openDay(date: LocalDate) {
        mode = CalendarMode.DAY
        goTo(date)
    }

    fun setMode(newMode: CalendarMode) {
        mode = newMode
        goTo(selected)
    }

    fun refresh() = load()

    /** Re-reads quietly, used when the screen comes back (reminders may have changed elsewhere). */
    fun reload() = load(showSkeleton = false)

    private fun goTo(date: LocalDate) {
        selected = date
        if (covered()) publish() else load()
    }

    // ---- creating

    /** Opens the create sheet on [date]; [time] is the tapped half hour from the week or day grid, if any. */
    fun openCreate(kind: CreateKind = CreateKind.EVENT, date: LocalDate = selected, time: LocalTime? = null) {
        create = CreateSheetUi(CreateDraft.at(date, time, LocalDateTime.now(clock), kind))
        publish()
    }

    fun closeCreate() {
        create = null
        publish()
    }

    fun edit(change: (CreateDraft) -> CreateDraft) {
        val sheet = create ?: return
        if (sheet.saving) return
        create = sheet.copy(draft = change(sheet.draft), error = null)
        publish()
    }

    fun setKind(kind: CreateKind) = edit { it.copy(kind = kind) }
    fun setTitle(text: String) = edit { it.copy(title = text) }
    fun setDate(date: LocalDate) = edit { it.copy(date = date) }
    fun setStart(time: LocalTime) = edit { it.withStart(time) }
    fun setEnd(time: LocalTime) = edit { it.copy(end = time) }
    fun setAllDay(allDay: Boolean) = edit { it.copy(allDay = allDay) }
    fun setAlert(minutes: Int?) = edit { it.copy(alertMinutes = minutes) }
    fun setRepeat(rule: String?) = edit { it.copy(repeat = rule) }

    fun save() {
        val sheet = create ?: return
        if (sheet.saving) return
        val draft = sheet.draft
        draft.problem()?.let { message ->
            create = sheet.copy(error = message)
            publish()
            return
        }
        create = sheet.copy(status = CreateStatus.SAVING, error = null)
        publish()
        viewModelScope.launch {
            val zone = clock.zone
            val title = draft.title.trim()
            val result: Result<Unit> = when (draft.kind) {
                CreateKind.EVENT ->
                    calendar.createEvent(NewEvent(title, draft.startInstant(zone), draft.endInstant(zone), draft.allDay, alertMinutes = draft.alertMinutes)).map { }
                CreateKind.TASK -> tasks.create(NewTask(title = title, dueAt = draft.taskDue(zone), repeatRule = draft.repeat)).map { }
                CreateKind.REMINDER -> reminders.create(NewReminder(title = title, remindAt = draft.startInstant(zone), repeatRule = draft.repeat)).map { }
            }
            result
                .onSuccess {
                    create = null
                    selected = draft.date
                    load(showSkeleton = false)
                }
                .onFailure { e ->
                    create = CreateSheetUi(draft, CreateStatus.EDITING, e.message ?: "Couldn't save that")
                    publish()
                }
        }
    }

    // ---- looking at and removing an entry

    fun openDetail(id: String) {
        val item = itemsByDay.values.flatten().firstOrNull { it.id == id } ?: return
        if (item.kind == AgendaKind.TASK) return // tasks open their own page
        val day = item.start.atZone(clock.zone).toLocalDate()
        val whenLabel = "${DETAIL_DATE.format(day)} · ${if (item.allDay) "All day" else item.start.formatTime(clock.zone)}"
        detail = DetailUi(item.id, item.kind, item.title, whenLabel)
        publish()
    }

    fun closeDetail() {
        detail = null
        publish()
    }

    fun deleteDetail() {
        val current = detail?.takeIf { !it.deleting } ?: return
        detail = current.copy(deleting = true, error = null)
        publish()
        viewModelScope.launch {
            val result = if (current.kind == AgendaKind.EVENT) calendar.deleteEvent(current.id) else reminders.delete(current.id)
            result
                .onSuccess {
                    detail = null
                    load(showSkeleton = false)
                }
                .onFailure { e ->
                    detail = current.copy(deleting = false, error = e.message ?: "Couldn't delete that")
                    publish()
                }
        }
    }

    // ---- loading

    /** The range worth loading for the current day and view: the whole month grid, and a month ahead for Schedule. */
    private fun neededRange(): Pair<LocalDate, LocalDate> {
        val grid = MonthGrid.dates(YearMonth.from(selected))
        val to = if (mode == CalendarMode.SCHEDULE) maxOf(grid.last().plusDays(1), selected.plusDays(SCHEDULE_DAYS)) else grid.last().plusDays(1)
        return grid.first() to to
    }

    private fun covered(): Boolean {
        val (from, to) = neededRange()
        val loadedFrom = loadedFrom ?: return false
        val loadedTo = loadedTo ?: return false
        return !from.isBefore(loadedFrom) && !to.isAfter(loadedTo)
    }

    private fun load(showSkeleton: Boolean = true) {
        viewModelScope.launch {
            if (showSkeleton) _state.update { it.copy(isLoading = true, error = null) }
            val (fromDate, toDate) = neededRange()
            val projectsCall = async { projects.projects() }
            val agendaResult = calendar.agenda(fromDate.atStartOfDay(clock.zone).toInstant(), toDate.atStartOfDay(clock.zone).toInstant())
            projectsById = projectsCall.await().getOrDefault(emptyList()).associateBy { it.id }
            agendaResult
                .onSuccess { items ->
                    itemsByDay = items.groupBy { it.start.atZone(clock.zone).toLocalDate() }
                    loadedFrom = fromDate
                    loadedTo = toDate
                    publish()
                }
                .onFailure { e ->
                    // A quiet refresh that fails keeps what is on screen; only a first load shows the error.
                    _state.update { it.copy(isLoading = false, error = if (it.cells.isEmpty() && it.columns.isEmpty()) e.message ?: "Couldn't load calendar" else null) }
                }
        }
    }

    // ---- building what the screen shows

    private fun publish() {
        val month = YearMonth.from(selected)
        val dayItems = itemsByDay[selected].orEmpty()
        val columnDates = when (mode) {
            CalendarMode.WEEK -> MonthGrid.week(selected)
            CalendarMode.DAY -> listOf(selected)
            else -> emptyList()
        }
        val columns = columnDates.map(::column)
        val firstHour = columns.flatMap { it.blocks }.minOfOrNull { it.startMinute / 60 }
        _state.value = CalendarUiState(
            isLoading = false,
            mode = mode,
            monthLabel = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            yearLabel = month.year.toString(),
            cells = if (mode == CalendarMode.MONTH) MonthGrid.dates(month).map { cell(it, month) } else emptyList(),
            columns = columns,
            selectedLabel = SELECTED_FORMAT.format(selected),
            plannedLabel = if (dayItems.isEmpty()) "" else "${dayItems.size} planned",
            agenda = dayItems.sortedBy { it.start }.map(::row),
            schedule = if (mode == CalendarMode.SCHEDULE) schedule() else emptyList(),
            nowMinute = if (columnDates.contains(today)) LocalTime.now(clock).let { it.hour * 60 + it.minute } else null,
            scrollHour = (firstHour?.minus(1) ?: DEFAULT_SCROLL_HOUR).coerceIn(0, 23),
            create = create,
            detail = detail,
        )
    }

    private fun cell(date: LocalDate, month: YearMonth): DayCellUi {
        val items = itemsByDay[date].orEmpty().sortedWith(compareBy({ !it.allDay }, { it.start }))
        return DayCellUi(
            date = date,
            inMonth = YearMonth.from(date) == month,
            isToday = date == today,
            isSelected = date == selected,
            chips = items.take(MAX_CHIPS).map { ChipUi(it.id, it.title, it.projectId?.let(projectsById::get)?.colorHex, it.kind, it.done) },
            more = (items.size - MAX_CHIPS).coerceAtLeast(0),
        )
    }

    private fun row(item: AgendaItem): AgendaRowUi {
        val project = item.projectId?.let(projectsById::get)
        return AgendaRowUi(
            id = item.id,
            title = item.title,
            timeLabel = if (item.allDay) "All day" else item.start.formatTime(clock.zone),
            projectName = project?.name,
            projectColorHex = project?.colorHex,
            done = item.done,
            kind = item.kind,
        )
    }

    private fun schedule(): List<ScheduleDayUi> = (0 until SCHEDULE_DAYS).map { selected.plusDays(it) }.mapNotNull { date ->
        val items = itemsByDay[date].orEmpty().sortedWith(compareBy({ !it.allDay }, { it.start }))
        if (items.isEmpty()) null else ScheduleDayUi(date, SCHEDULE_DATE.format(date), date == today, items.map(::row))
    }

    private fun column(date: LocalDate): DayColumnUi {
        val items = itemsByDay[date].orEmpty()
        val origin = date.atStartOfDay(clock.zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(clock.zone).toInstant()
        val placed = TimelineLayout.place(items.filterNot { it.allDay }, { it.start }, { it.end })
        return DayColumnUi(
            date = date,
            letter = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH),
            isToday = date == today,
            isSelected = date == selected,
            allDay = items.filter { it.allDay }.map(::row),
            blocks = placed.map { p ->
                val start = Duration.between(origin, p.start).toMinutes().toInt().coerceAtLeast(0)
                val end = Duration.between(origin, minOf(p.end, dayEnd)).toMinutes().toInt()
                TimelineBlockUi(
                    id = p.item.id,
                    title = p.item.title,
                    timeLabel = p.item.start.formatTime(clock.zone),
                    colorHex = p.item.projectId?.let(projectsById::get)?.colorHex,
                    kind = p.item.kind,
                    done = p.item.done,
                    startMinute = start,
                    durationMinutes = (end - start).coerceAtLeast(MIN_BLOCK_MINUTES),
                    lane = p.lane,
                    laneCount = p.laneCount,
                )
            },
        )
    }

    private companion object {
        val SELECTED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMMM", Locale.ENGLISH)
        val SCHEDULE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
        val DETAIL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
        const val MIN_BLOCK_MINUTES = 30
    }
}
