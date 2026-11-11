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
import com.utkarsh.todo.feature.calendar.ui.AgendaRowUi
import com.utkarsh.todo.feature.calendar.ui.CalendarActions
import com.utkarsh.todo.feature.calendar.ui.CalendarMode
import com.utkarsh.todo.feature.calendar.ui.CalendarScreen
import com.utkarsh.todo.feature.calendar.ui.CalendarUiState
import com.utkarsh.todo.feature.calendar.ui.ChipUi
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
        columns = when (mode) { CalendarMode.WEEK -> columns(7); CalendarMode.THREE_DAY -> columns(3); CalendarMode.DAY -> columns(1); else -> emptyList() },
        selectedLabel = "Sat, 7 November", plannedLabel = if (agenda.isEmpty()) "" else "${agenda.size} planned", agenda = agenda,
        schedule = if (mode == CalendarMode.SCHEDULE) listOf(ScheduleDayUi(selected, "Sat 7 Nov", true, listOf(row, taskRow)), ScheduleDayUi(selected.plusDays(7), "Sat 14 Nov", false, listOf(row.copy(id = "b", title = "Next week")))) else emptyList(),
        nowMinute = if (mode == CalendarMode.WEEK || mode == CalendarMode.DAY || mode == CalendarMode.THREE_DAY) 600 else null,
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
                        onCreate = { date, time -> events += "create:$date:${time ?: "-"}" },
                        onEditDetail = { events += "edit" },
                        onOpenItem = { id, kind -> events += "open:$id:$kind" },
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

        assertEquals(listOf("create:2026-11-03:03:00", "create:2026-11-01:05:30"), events)
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

    // ---- 3 days, and creating only through the + button

    @Test
    fun the_3_day_view_shows_three_columns_and_a_grid() {
        show(state(CalendarMode.THREE_DAY))

        compose.onNodeWithTag("time-grid-three_day").assertIsDisplayed()
        (1..3).forEach { compose.onNodeWithTag("column-2026-11-0$it").assertIsDisplayed() }
        compose.onNodeWithTag("column-2026-11-04").assertDoesNotExist()
        compose.onNodeWithText("2026 · 3 DAYS").assertIsDisplayed()
    }

    @Test
    fun the_mode_menu_offers_3_days_and_reports_it() {
        show(state())
        compose.onNodeWithTag("mode-menu").performClick()
        compose.onNodeWithTag("mode-THREE_DAY").assertIsDisplayed()
        compose.onNodeWithText("3 days").assertIsDisplayed()
        compose.onNodeWithTag("mode-THREE_DAY").performClick()
        assertEquals(listOf("mode:THREE_DAY"), events)
    }

    @Test
    fun tapping_an_empty_slot_in_the_3_day_view_creates_at_that_day_and_half_hour() {
        show(state(CalendarMode.THREE_DAY))
        val hourPx = with(compose.density) { 56.dp.toPx() }

        // second of three columns (Mon 2 Nov), 10:10 -> the 10:00 slot
        compose.onNodeWithTag("slots").performTouchInput { click(Offset(width * (1.5f / 3f), hourPx * 10 + 6f)) }

        assertEquals(listOf("create:2026-11-02:10:00"), events)
    }

    @Test
    fun there_is_no_plus_button_here_the_one_in_the_bar_is_the_only_way_to_create() {
        show(state())
        compose.onNodeWithTag("calendar-fab").assertDoesNotExist()
    }

    // ---- creating and editing from a date

    @Test
    fun the_agenda_has_a_plus_add_that_creates_on_the_selected_day() {
        show(state())
        compose.onNodeWithTag("agenda-add").assertIsDisplayed()
        compose.onNodeWithTag("agenda-add").performClick()
        assertEquals(listOf("create:2026-11-07:-"), events)
    }

    @Test
    fun tapping_the_selected_date_again_creates_there_but_another_date_just_selects_it() {
        show(state())
        compose.onNodeWithTag("day-2026-11-12").performClick()
        compose.onNodeWithTag("day-2026-11-07").performClick() // already selected
        assertEquals(listOf("select:2026-11-12", "create:2026-11-07:-"), events)
    }

    @Test
    fun an_event_can_be_edited_from_its_detail_but_an_alert_only_deleted() {
        show(state().copy(detail = DetailUi("a", AgendaKind.EVENT, "Design review", "Sat 7 Nov · 2:00 PM")))
        compose.onNodeWithTag("detail-edit").performClick()
        assertEquals(listOf("edit"), events)
    }

    @Test
    fun an_alert_has_no_edit_button() {
        show(state().copy(detail = DetailUi("r", AgendaKind.REMINDER, "Drink water", "Sat 7 Nov · 11:00 AM")))
        compose.onNodeWithTag("detail-edit").assertDoesNotExist()
        compose.onNodeWithTag("detail-delete").assertIsDisplayed()
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
        compose.onNodeWithText("ALERT").assertIsDisplayed()
        compose.onNodeWithTag("detail-error").assertIsDisplayed()
    }
}
