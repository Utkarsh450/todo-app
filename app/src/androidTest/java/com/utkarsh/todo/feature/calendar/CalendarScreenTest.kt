package com.utkarsh.todo.feature.calendar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.calendar.domain.AgendaKind
import com.utkarsh.todo.feature.calendar.domain.CreateDraft
import com.utkarsh.todo.feature.calendar.domain.CreateKind
import com.utkarsh.todo.feature.calendar.ui.AgendaRowUi
import com.utkarsh.todo.feature.calendar.ui.CalendarActions
import com.utkarsh.todo.feature.calendar.ui.CalendarMode
import com.utkarsh.todo.feature.calendar.ui.CalendarScreen
import com.utkarsh.todo.feature.calendar.ui.CalendarUiState
import com.utkarsh.todo.feature.calendar.ui.ChipUi
import com.utkarsh.todo.feature.calendar.ui.CreateSheetActions
import com.utkarsh.todo.feature.calendar.ui.CreateSheetUi
import com.utkarsh.todo.feature.calendar.ui.CreateStatus
import com.utkarsh.todo.feature.calendar.ui.DayCellUi
import com.utkarsh.todo.feature.calendar.ui.DayColumnUi
import com.utkarsh.todo.feature.calendar.ui.DetailUi
import com.utkarsh.todo.feature.calendar.ui.ScheduleDayUi
import com.utkarsh.todo.feature.calendar.ui.TimelineBlockUi
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Google-style calendar: month, week, day and schedule views, the "+" menu, the create sheet and the detail sheet. */
class CalendarScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val selected = LocalDate.of(2026, 11, 7)

    private val cells = generateSequence(LocalDate.of(2026, 11, 1)) { it.plusDays(1) }.take(35).map {
        DayCellUi(
            it, inMonth = it.monthValue == 11, isToday = it == selected, isSelected = it == selected,
            chips = if (it == selected) listOf(ChipUi("a", "Review PR #482", "#2784D5", AgendaKind.EVENT, false), ChipUi("t", "Pay rent", null, AgendaKind.TASK, false)) else emptyList(),
            more = if (it == selected) 2 else 0,
        )
    }.toList()

    private val row = AgendaRowUi("a", "Review PR #482", "9:00 AM", "Engineering", "#2784D5", done = false, kind = AgendaKind.EVENT)
    private val taskRow = AgendaRowUi("t", "Pay rent", "11:00 AM", null, null, done = false, kind = AgendaKind.TASK)

    private fun columns(count: Int) = (0 until count).map { i ->
        val date = if (count == 1) selected else LocalDate.of(2026, 11, 1).plusDays(i.toLong())
        DayColumnUi(
            date, "SMTWTFS"[i % 7].toString(), isToday = date == selected, isSelected = date == selected,
            allDay = if (i == 0) listOf(AgendaRowUi("ad", "Holiday", "All day", null, null, false, AgendaKind.EVENT)) else emptyList(),
            blocks = if (date == selected) listOf(TimelineBlockUi("a", "Review PR #482", "9:00 AM", "#2784D5", AgendaKind.EVENT, false, 540, 90, 0, 1)) else emptyList(),
        )
    }

    private fun state(mode: CalendarMode = CalendarMode.MONTH, agenda: List<AgendaRowUi> = listOf(row, taskRow)) = CalendarUiState(
        isLoading = false, mode = mode, monthLabel = "November", yearLabel = "2026",
        cells = if (mode == CalendarMode.MONTH) cells else emptyList(),
        columns = when (mode) { CalendarMode.WEEK -> columns(7); CalendarMode.DAY -> columns(1); else -> emptyList() },
        selectedLabel = "Sat, 7 November", plannedLabel = if (agenda.isEmpty()) "" else "${agenda.size} planned", agenda = agenda,
        schedule = if (mode == CalendarMode.SCHEDULE) listOf(ScheduleDayUi(selected, "Sat 7 Nov", true, listOf(row, taskRow)), ScheduleDayUi(selected.plusDays(7), "Sat 14 Nov", false, listOf(row.copy(id = "b", title = "Next week")))) else emptyList(),
        nowMinute = if (mode == CalendarMode.WEEK || mode == CalendarMode.DAY) 600 else null,
        scrollHour = 0,
    )

    private fun show(state: CalendarUiState) {
        events.clear()
        compose.setContent {
            PaperTheme {
                CalendarScreen(
                    state = state,
                    actions = CalendarActions(
                        onPrevious = { events += "prev" },
                        onNext = { events += "next" },
                        onToday = { events += "today" },
                        onSelect = { events += "select:$it" },
                        onOpenDay = { events += "day:$it" },
                        onModeChange = { events += "mode:$it" },
                        onRetry = { events += "retry" },
                        onCreate = { kind, date, time -> events += "create:$kind:${date ?: "-"}:${time ?: "-"}" },
                        onOpenItem = { id, kind -> events += "open:$id:$kind" },
                        create = CreateSheetActions(
                            onKind = { events += "kind:$it" }, onTitle = { events += "title:$it" }, onAllDay = { events += "allday:$it" },
                            onAlert = { events += "alert:$it" }, onRepeat = { events += "repeat:$it" }, onSave = { events += "save" }, onClose = { events += "close" },
                        ),
                        onCloseDetail = { events += "close-detail" },
                        onDeleteDetail = { events += "delete" },
                    ),
                )
            }
        }
    }

    // ---- month

    @Test
    fun month_view_shows_title_grid_bars_and_agenda() {
        show(state())

        compose.onNodeWithTag("mode-menu").assertIsDisplayed()
        compose.onNodeWithText("November").assertIsDisplayed()
        compose.onNodeWithText("2026 · MONTH").assertIsDisplayed()
        compose.onNodeWithTag("day-2026-11-07").assertIsDisplayed()
        compose.onNodeWithTag("chip-a", useUnmergedTree = true).assertIsDisplayed() // inside a clickable cell
        compose.onNodeWithTag("more-2026-11-07", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Sat, 7 November").assertIsDisplayed()
        compose.onNodeWithText("2 PLANNED").assertIsDisplayed()
        compose.onNodeWithTag("agenda-a").assertIsDisplayed()
        compose.onNodeWithText("ENGINEERING").assertIsDisplayed()
        compose.onNodeWithText("TASK").assertIsDisplayed()
    }

    @Test
    fun navigation_selection_and_opening_an_entry() {
        show(state())

        compose.onNodeWithTag("calendar-prev").performClick()
        compose.onNodeWithTag("calendar-next").performClick()
        compose.onNodeWithTag("go-today").performClick()
        compose.onNodeWithTag("day-2026-11-12").performClick()
        compose.onNodeWithTag("agenda-t").performClick()

        assertEquals(listOf("prev", "next", "today", "select:2026-11-12", "open:t:TASK"), events)
    }

    @Test
    fun the_title_opens_a_menu_of_views() {
        show(state())

        compose.onNodeWithTag("mode-menu").performClick()
        CalendarMode.entries.forEach { compose.onNodeWithTag("mode-${it.name}").assertIsDisplayed() }
        compose.onNodeWithTag("mode-WEEK").performClick()

        assertEquals(listOf("mode:WEEK"), events)
    }

    @Test
    fun empty_day_invites_adding_something() {
        show(state(agenda = emptyList()))
        compose.onNodeWithText("Nothing planned. Tap + to add something.").assertIsDisplayed()
    }

    @Test
    fun error_state_offers_retry() {
        show(CalendarUiState(isLoading = false, error = "offline", monthLabel = "November", yearLabel = "2026"))
        compose.onNodeWithText("Can't reach your calendar").assertIsDisplayed()
        compose.onNodeWithTag("calendar-retry").performClick()
        assertEquals(listOf("retry"), events)
    }

    // ---- week and day

    @Test
    fun week_view_shows_seven_days_a_grid_a_block_and_the_now_line() {
        show(state(CalendarMode.WEEK))

        compose.onNodeWithTag("time-grid-week").assertIsDisplayed()
        (1..7).forEach { compose.onNodeWithTag("column-2026-11-0$it").assertIsDisplayed() }
        compose.onNodeWithTag("allday-ad").assertIsDisplayed()
        compose.onNodeWithTag("block-a").assertIsDisplayed()
        compose.onNodeWithTag("now-line").assertExists()
        compose.onNodeWithText("2026 · WEEK").assertIsDisplayed()
    }

    @Test
    fun a_block_opens_and_a_day_header_opens_that_day() {
        show(state(CalendarMode.WEEK))

        compose.onNodeWithTag("block-a").performClick()
        compose.onNodeWithTag("column-2026-11-03").performClick()

        assertEquals(listOf("open:a:EVENT", "day:2026-11-03"), events)
    }

    @Test
    fun tapping_an_empty_slot_creates_at_that_day_and_half_hour() {
        show(state(CalendarMode.WEEK))
        val hourPx = with(compose.density) { 56.dp.toPx() }

        // third column (Tue 3 Nov), 03:10 -> the 03:00 slot
        compose.onNodeWithTag("slots").performTouchInput { click(Offset(width * (2.5f / 7f), hourPx * 3 + 6f)) }
        // first column (Sun 1 Nov), 05:40 -> the 05:30 slot
        compose.onNodeWithTag("slots").performTouchInput { click(Offset(width * (0.5f / 7f), hourPx * 5 + hourPx * 0.67f)) }

        assertEquals(listOf("create:EVENT:2026-11-03:03:00", "create:EVENT:2026-11-01:05:30"), events)
    }

    @Test
    fun day_view_is_one_wide_column() {
        show(state(CalendarMode.DAY))

        compose.onNodeWithTag("time-grid-day").assertIsDisplayed()
        compose.onNodeWithTag("column-2026-11-07").assertIsDisplayed()
        compose.onNodeWithTag("block-a").assertIsDisplayed()
        compose.onNodeWithText("9:00 AM").assertExists() // a wide block also shows its time
    }

    // ---- schedule

    @Test
    fun schedule_groups_entries_by_day() {
        show(state(CalendarMode.SCHEDULE))

        compose.onNodeWithTag("schedule").assertIsDisplayed()
        compose.onNodeWithText("SAT 7 NOV · TODAY").assertIsDisplayed()
        compose.onNodeWithText("SAT 14 NOV").assertExists()
        compose.onNodeWithTag("agenda-b").performClick()
        assertEquals(listOf("open:b:EVENT"), events)
    }

    @Test
    fun an_empty_schedule_says_so() {
        show(state(CalendarMode.SCHEDULE).copy(schedule = emptyList()))
        compose.onNodeWithTag("schedule-empty").assertIsDisplayed()
    }

    // ---- the plus menu

    @Test
    fun the_plus_button_fans_out_event_task_and_reminder() {
        show(state())
        compose.onNodeWithTag("fab-task").assertDoesNotExist()

        compose.onNodeWithTag("calendar-fab").performClick()
        listOf("fab-event", "fab-task", "fab-reminder").forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        compose.onNodeWithTag("fab-reminder").performClick()

        assertEquals(listOf("create:REMINDER:-:-"), events)
        compose.onNodeWithTag("fab-task").assertDoesNotExist() // closes after choosing
    }

    // ---- the create sheet

    private fun sheet(kind: CreateKind = CreateKind.EVENT, title: String = "", error: String? = null, status: CreateStatus = CreateStatus.EDITING, allDay: Boolean = false) =
        state().copy(create = CreateSheetUi(CreateDraft(kind, title, selected, LocalTime.of(11, 0), allDay = allDay), status, error))

    @Test
    fun the_event_sheet_shows_when_and_the_alert_choices_with_30_minutes_chosen() {
        show(sheet(title = "Gym"))

        compose.onNodeWithTag("create-sheet").assertIsDisplayed()
        compose.onNodeWithTag("create-date").assertIsDisplayed()
        compose.onNodeWithText("Sat, 7 Nov 2026").assertIsDisplayed()
        compose.onNodeWithTag("create-start").assertTextContains("11:00 AM")
        compose.onNodeWithTag("create-end").assertTextContains("12:00 PM")
        compose.onNodeWithText("ALERT BEFORE").assertIsDisplayed()
        listOf("None", "At time", "10 min", "30 min", "1 hour").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Save event").assertIsDisplayed()
    }

    @Test
    fun the_sheet_reports_what_is_chosen() {
        show(sheet())

        compose.onNodeWithText("Task").performClick()
        compose.onNodeWithTag("create-title").performTextReplacement("Gym")
        compose.onNodeWithTag("alert-60").performClick()
        compose.onNodeWithTag("alert-none").performClick()
        compose.onNodeWithTag("create-save").performClick()

        // Focusing the field in a test can report a dismiss request for the sheet window; it is not part of what is chosen.
        assertEquals(listOf("kind:TASK", "title:Gym", "alert:60", "alert:null", "save"), events - "close")
    }

    @Test
    fun a_task_or_reminder_offers_repeat_instead_of_an_alert() {
        show(sheet(CreateKind.REMINDER))

        compose.onNodeWithText("REPEAT").assertIsDisplayed()
        compose.onNodeWithText("ALERT BEFORE").assertDoesNotExist()
        compose.onNodeWithText("ALL DAY").assertDoesNotExist() // a reminder always has a time
        compose.onNodeWithTag("create-end").assertDoesNotExist()
        compose.onNodeWithTag("repeat-Every Week").performClick()
        compose.onNodeWithText("Save reminder").assertIsDisplayed()

        assertEquals(listOf("repeat:Every Week"), events)
    }

    @Test
    fun all_day_hides_the_times() {
        show(sheet(allDay = true))
        compose.onNodeWithTag("create-start").assertDoesNotExist()
        compose.onNodeWithTag("create-end").assertDoesNotExist()
    }

    @Test
    fun a_problem_is_shown_above_the_save_button() {
        show(sheet(error = "Give it a title"))
        compose.onNodeWithTag("create-error").assertIsDisplayed()
        compose.onNodeWithText("Give it a title").assertIsDisplayed()

    }

    // ---- the detail sheet

    @Test
    fun an_opened_event_can_be_deleted() {
        show(state().copy(detail = DetailUi("a", AgendaKind.EVENT, "Design review", "Sat 7 Nov · 2:00 PM")))

        compose.onNodeWithTag("detail-sheet").assertIsDisplayed()
        compose.onNodeWithTag("detail-title").assertIsDisplayed()
        compose.onNodeWithText("Design review").assertIsDisplayed()
        compose.onNodeWithText("Sat 7 Nov · 2:00 PM").assertIsDisplayed()
        compose.onNodeWithText("EVENT").assertIsDisplayed()
        compose.onNodeWithTag("detail-delete").performClick()

        assertEquals(listOf("delete"), events)
    }

    @Test
    fun a_failed_delete_shows_why() {
        show(state().copy(detail = DetailUi("r", AgendaKind.REMINDER, "Drink water", "Sat 7 Nov · 11:00 AM", error = "offline")))
        compose.onNodeWithText("REMINDER").assertIsDisplayed()
        compose.onNodeWithTag("detail-error").assertIsDisplayed()
    }
}
