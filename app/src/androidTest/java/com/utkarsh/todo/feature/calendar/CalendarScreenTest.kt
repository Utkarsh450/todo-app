package com.utkarsh.todo.feature.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.calendar.ui.AgendaRowUi
import com.utkarsh.todo.feature.calendar.ui.CalendarMode
import com.utkarsh.todo.feature.calendar.ui.CalendarScreen
import com.utkarsh.todo.feature.calendar.ui.CalendarUiState
import com.utkarsh.todo.feature.calendar.ui.DayCellUi
import com.utkarsh.todo.feature.calendar.ui.TimelineBlockUi
import com.utkarsh.todo.feature.calendar.ui.TimelineUi
import com.utkarsh.todo.feature.calendar.ui.WeekDayUi
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalendarScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val selected = LocalDate.of(2026, 11, 7)

    private val cells = generateSequence(LocalDate.of(2026, 11, 1)) { it.plusDays(1) }.take(35).map {
        DayCellUi(it, inMonth = it.monthValue == 11, isToday = it == selected, isSelected = it == selected, dots = if (it == selected) listOf("#2784D5", null) else emptyList())
    }.toList()

    private val row = AgendaRowUi("a", "Review PR #482", "9:00 AM", "Engineering", "#2784D5", done = false, isEvent = true)

    private fun state(mode: CalendarMode = CalendarMode.MONTH, agenda: List<AgendaRowUi> = listOf(row)) = CalendarUiState(
        isLoading = false, mode = mode, monthLabel = "November", yearLabel = "2026", cells = cells,
        weekDays = (1..7).map { WeekDayUi(LocalDate.of(2026, 11, it), "SMTWTFS"[it - 1].toString(), it == 7, it == 7, it == 7) },
        selectedLabel = "Sat, 7 November", plannedLabel = if (agenda.isEmpty()) "" else "${agenda.size} planned", agenda = agenda,
        timeline = TimelineUi(9, 18, listOf(TimelineBlockUi("a", "Review PR #482", "9:00 AM", "#2784D5", 0, 90, 0, 1)), emptyList()),
    )

    private fun show(state: CalendarUiState) {
        events.clear()
        compose.setContent {
            PaperTheme {
                CalendarScreen(
                    state = state,
                    onPrevious = { events += "prev" },
                    onNext = { events += "next" },
                    onToday = { events += "today" },
                    onSelect = { events += "select:$it" },
                    onModeChange = { events += "mode:$it" },
                    onRetry = { events += "retry" },
                )
            }
        }
    }

    @Test
    fun month_view_shows_title_grid_and_agenda() {
        show(state())

        compose.onNodeWithTag("month-title").assertIsDisplayed()
        compose.onNodeWithText("November").assertIsDisplayed()
        compose.onNodeWithTag("day-2026-11-07").assertIsDisplayed()
        compose.onNodeWithText("Sat, 7 November").assertIsDisplayed()
        compose.onNodeWithText("1 PLANNED").assertIsDisplayed()
        compose.onNodeWithText("Review PR #482").assertIsDisplayed()
        compose.onNodeWithText("ENGINEERING").assertIsDisplayed()
    }

    @Test
    fun navigation_and_selection_callbacks() {
        show(state())

        compose.onNodeWithTag("Previous month").performClick()
        compose.onNodeWithTag("Next month").performClick()
        compose.onNodeWithTag("go-today").performClick()
        compose.onNodeWithTag("day-2026-11-12").performClick()

        assertEquals(listOf("prev", "next", "today", "select:2026-11-12"), events)
    }

    @Test
    fun mode_switch_reports_week() {
        show(state())
        compose.onNodeWithText("Week").performClick()
        assertEquals(listOf("mode:WEEK"), events)
    }

    @Test
    fun empty_day_shows_friendly_message() {
        show(state(agenda = emptyList()))
        compose.onNodeWithText("Nothing planned. Enjoy the quiet.").assertIsDisplayed()
    }

    @Test
    fun week_view_shows_strip_and_timeline_blocks() {
        show(state(CalendarMode.WEEK))

        compose.onNodeWithTag("week-2026-11-07").assertIsDisplayed()
        compose.onNodeWithTag("timeline").assertIsDisplayed()
        compose.onNodeWithTag("block-a").assertIsDisplayed()
        compose.onNodeWithText("9A").assertIsDisplayed()

        compose.onNodeWithTag("week-2026-11-03").performClick()
        assertEquals(listOf("select:2026-11-03"), events)
    }

    @Test
    fun error_state_offers_retry() {
        show(CalendarUiState(isLoading = false, error = "offline", monthLabel = "November", yearLabel = "2026"))
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf("retry"), events)
    }
}
