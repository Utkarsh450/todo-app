package com.utkarsh.todo.feature.taskdetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.taskdetail.ui.RemindState
import com.utkarsh.todo.feature.taskdetail.ui.RemindUi
import com.utkarsh.todo.feature.taskdetail.ui.TaskDetailScreen
import com.utkarsh.todo.feature.taskdetail.ui.TaskDetailUi
import com.utkarsh.todo.feature.taskdetail.ui.TaskDetailUiState
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Subtask
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TaskDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val task = TaskDetailUi(
        id = "t1", title = "Ship onboarding redesign", statusLabel = "IN PROGRESS", dueLabel = "Today, 2:00 PM",
        priority = Priority.URGENT, projectName = "Design", projectColorHex = "#935EDF", repeat = null,
        notes = "Keep it to three screens.",
        subtasks = listOf(Subtask("s1", "t1", "Audit current flow", true), Subtask("s2", "t1", "Wireframes", false)),
        done = false,
    )

    private fun show(state: TaskDetailUiState) {
        events.clear()
        compose.setContent {
            PaperTheme {
                TaskDetailScreen(
                    state = state,
                    onBack = { events += "back" },
                    onToggleDone = { events += "done" },
                    onToggleSubtask = { events += "sub:$it" },
                    onAddSubtask = { events += "add:$it" },
                    onDelete = { events += "delete" },
                    onRetry = { events += "retry" },
                    onBreakDown = { events += "breakdown" },
                    onToggleRemind = { events += "remind" },
                    onNotesChange = { events += "notes:$it" },
                )
            }
        }
    }

    @Test
    fun renders_task_details() {
        show(TaskDetailUiState(isLoading = false, task = task))

        compose.onNodeWithText("IN PROGRESS").assertIsDisplayed()
        compose.onNodeWithTag("detail-title").assertIsDisplayed()
        compose.onNodeWithText("Today, 2:00 PM").assertIsDisplayed()
        compose.onNodeWithText("⚑ Urgent").assertIsDisplayed()
        compose.onNodeWithText("Design").assertIsDisplayed()
        compose.onNodeWithText("1/2").assertIsDisplayed()
        compose.onNodeWithText("Keep it to three screens.").assertIsDisplayed()
    }

    @Test
    fun actions_are_reported() {
        show(TaskDetailUiState(isLoading = false, task = task))

        compose.onNodeWithContentDescription("Subtask Wireframes").performClick()
        compose.onNodeWithTag("toggle-done").performClick()
        compose.onNodeWithTag("delete-task").performClick()
        compose.onNodeWithTag("confirm-delete").performClick()
        compose.onNodeWithText("‹ Today").performClick()

        assertEquals(listOf("sub:s2", "done", "delete", "back"), events)
    }

    @Test
    fun delete_asks_first_and_cancel_keeps_the_task() {
        show(TaskDetailUiState(isLoading = false, task = task))

        compose.onNodeWithTag("delete-task").performClick()
        compose.onNodeWithText("Delete this task?").assertIsDisplayed()
        assertEquals(emptyList<String>(), events)

        compose.onNodeWithTag("cancel-delete").performClick()

        compose.onNodeWithText("Delete this task?").assertDoesNotExist()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun the_remind_row_offers_a_reminder_and_reports_taps() {
        show(TaskDetailUiState(isLoading = false, task = task.copy(remind = RemindUi(RemindState.OFF, "+ 1h before due"))))

        compose.onNodeWithText("+ 1h before due").assertIsDisplayed()
        compose.onNodeWithTag("remind-row").performClick()

        assertEquals(listOf("remind"), events)
    }

    @Test
    fun the_remind_row_shows_when_a_reminder_is_on_and_lets_it_be_removed() {
        show(TaskDetailUiState(isLoading = false, task = task.copy(remind = RemindUi(RemindState.ON, "⏰ Today, 1:00 PM"))))

        compose.onNodeWithText("⏰ Today, 1:00 PM").assertIsDisplayed()
        compose.onNodeWithText("REMOVE").assertIsDisplayed()
        compose.onNodeWithTag("remind-row").performClick()

        assertEquals(listOf("remind"), events)
    }

    @Test
    fun the_remind_row_does_nothing_without_a_usable_due_time() {
        show(TaskDetailUiState(isLoading = false, task = task.copy(remind = RemindUi(RemindState.NEEDS_DUE, "Add a due date first"))))

        compose.onNodeWithText("Add a due date first").assertIsDisplayed()
        compose.onNodeWithTag("remind-row").performClick()

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun notes_are_edited_in_place() {
        show(TaskDetailUiState(isLoading = false, task = task.copy(notes = "")))

        compose.onNodeWithText("+ Add notes").assertIsDisplayed()
        compose.onNodeWithTag("notes-input").performTextInput("Call Sam")

        assertEquals(listOf("notes:Call Sam"), events)
    }

    @Test
    fun add_subtask_submits_on_ime_done() {
        show(TaskDetailUiState(isLoading = false, task = task))

        compose.onNodeWithTag("add-subtask-input").performTextInput("Usability test")
        compose.onNodeWithTag("add-subtask-input").performImeAction()

        assertEquals(listOf("add:Usability test"), events)
    }

    @Test
    fun break_it_down_opens_the_assistant() {
        show(TaskDetailUiState(isLoading = false, task = task))

        compose.onNodeWithTag("break-it-down").assertIsDisplayed().performClick()

        assertEquals(listOf("breakdown"), events)
    }

    @Test
    fun done_task_offers_reopen() {
        show(TaskDetailUiState(isLoading = false, task = task.copy(done = true, statusLabel = "DONE")))
        compose.onNodeWithText("Reopen").assertIsDisplayed()
    }

    @Test
    fun empty_subtasks_show_friendly_text() {
        show(TaskDetailUiState(isLoading = false, task = task.copy(subtasks = emptyList())))
        compose.onNodeWithText("No subtasks. Keep it simple.").assertIsDisplayed()
    }

    @Test
    fun load_error_offers_retry() {
        show(TaskDetailUiState(isLoading = false, error = "offline"))

        compose.onNodeWithText("Couldn't open this task").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(listOf("retry"), events)
    }
}
