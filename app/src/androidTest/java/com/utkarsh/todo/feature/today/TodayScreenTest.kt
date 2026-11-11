package com.utkarsh.todo.feature.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.today.domain.TaskItem
import com.utkarsh.todo.feature.today.domain.TodayView
import com.utkarsh.todo.feature.today.domain.UndoPrompt
import com.utkarsh.todo.feature.today.ui.DueSoonSummary
import com.utkarsh.todo.feature.today.ui.TodayScreen
import com.utkarsh.todo.feature.today.ui.TodayUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TodayScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun item(id: String, title: String, done: Boolean = false, pinned: Boolean = false) = TaskItem(
        id = id, title = title, timeLabel = "9:00 AM", done = done, priority = Priority.NORMAL, pinned = pinned,
        projectName = "Design", projectColorHex = "#935EDF", subtasksDone = 3, subtasksTotal = 5,
    )

    private val events = mutableListOf<String>()

    private fun show(state: TodayUiState, withDueSoon: Boolean = false) {
        events.clear()
        compose.setContent {
            PaperTheme {
                TodayScreen(
                    state = state,
                    onViewChange = { events += "view:$it" },
                    onToggle = { events += "toggle:$it" },
                    onOpen = { events += "open:$it" },
                    onMoveToTomorrow = { events += "move:$it" },
                    onArchive = { events += "archive:$it" },
                    onUndo = { events += "undo" },
                    onDismissUndo = {},
                    onRetry = { events += "retry" },
                    onDueSoon = if (withDueSoon) ({ events += "due-soon" }) else null,
                )
            }
        }
    }

    private fun loaded(vararg items: TaskItem, view: TodayView = TodayView.BLOCKS, undo: UndoPrompt? = null) =
        TodayUiState(isLoading = false, caption = "SAT 7 NOV · W45", items = items.toList(), view = view, undo = undo)

    @Test
    fun shows_header_remaining_count_and_tasks() {
        show(loaded(item("a", "Review PR"), item("b", "Groceries", done = true)))

        compose.onNodeWithText("SAT 7 NOV · W45").assertIsDisplayed()
        compose.onNodeWithTag("today-title").assertIsDisplayed()
        compose.onNodeWithText("Review PR").assertIsDisplayed()
        compose.onNodeWithText("1 LEFT").assertIsDisplayed()
    }

    @Test
    fun shows_pinned_card_for_the_pinned_task() {
        show(loaded(item("a", "Ship onboarding redesign", pinned = true)))

        compose.onNodeWithText("PINNED").assertIsDisplayed()
    }

    @Test
    fun checkbox_and_row_tap_report_the_right_task() {
        show(loaded(item("a", "Review PR")))

        compose.onNodeWithContentDescription("Complete Review PR").performClick()
        compose.onNodeWithText("Review PR").performClick()

        assertEquals(listOf("toggle:a", "open:a"), events)
    }

    @Test
    fun swiping_right_moves_to_tomorrow() {
        show(loaded(item("a", "Review PR")))

        compose.onNodeWithTag("task-a").performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertEquals(listOf("move:a"), events)
    }

    @Test
    fun swiping_left_archives() {
        show(loaded(item("a", "Review PR")))

        compose.onNodeWithTag("task-a").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(listOf("archive:a"), events)
    }

    @Test
    fun undo_snackbar_triggers_undo() {
        show(loaded(item("a", "Review PR"), undo = UndoPrompt("Archived")))

        compose.onNodeWithText("Archived").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()

        assertEquals(listOf("undo"), events)
    }

    @Test
    fun view_switch_reports_plan() {
        show(loaded(item("a", "Review PR")))

        compose.onNodeWithText("Plan").performClick()

        assertEquals(listOf("view:PLAN"), events)
    }

    @Test
    fun plan_view_lists_agenda_with_project_tag() {
        show(loaded(item("a", "Review PR"), view = TodayView.PLAN))

        compose.onNodeWithText("Review PR").assertIsDisplayed()
        compose.onNodeWithText("DESIGN").assertIsDisplayed()
        compose.onNodeWithText("Evening is free.").assertIsDisplayed()
    }

    @Test
    fun error_state_offers_retry() {
        show(TodayUiState(isLoading = false, errorMessage = "offline"))

        compose.onNodeWithText("Can't reach your tasks").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(listOf("retry"), events)
    }

    @Test
    fun due_soon_banner_says_what_is_overdue_and_due_today_and_opens_the_screen() {
        show(loaded(item("a", "Review PR")).copy(dueSoon = DueSoonSummary(overdue = 1, today = 4)), withDueSoon = true)

        compose.onNodeWithTag("due-soon-banner").assertIsDisplayed()
        compose.onNodeWithText("1 overdue · 4 alerts today").assertIsDisplayed()
        compose.onNodeWithText("DUE SOON →").assertIsDisplayed()
        compose.onNodeWithTag("due-soon-banner").performClick()

        assertEquals(listOf("due-soon"), events)
    }

    @Test
    fun due_soon_banner_uses_the_singular_and_leaves_out_what_is_zero() {
        show(loaded(item("a", "Review PR")).copy(dueSoon = DueSoonSummary(overdue = 0, today = 1)))
        compose.onNodeWithText("1 alert today").assertIsDisplayed()
    }

    @Test
    fun there_is_no_banner_when_nothing_is_due_soon() {
        show(loaded(item("a", "Review PR")))
        compose.onNodeWithTag("due-soon-banner").assertDoesNotExist()
    }
}
