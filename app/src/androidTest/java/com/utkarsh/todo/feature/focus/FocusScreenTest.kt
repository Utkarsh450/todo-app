package com.utkarsh.todo.feature.focus

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.focus.ui.FocusPhase
import com.utkarsh.todo.feature.focus.ui.FocusScreen
import com.utkarsh.todo.feature.focus.ui.FocusUiState
import com.utkarsh.todo.feature.focus.ui.FocusViewModel
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Subtask
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FocusScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: FocusUiState) {
        events.clear()
        compose.setContent {
            PaperTheme {
                FocusScreen(
                    state, onBack = { events += "back" }, onMinutes = { events += "min:$it" }, onStart = { events += "start" },
                    onPause = { events += "pause" }, onReset = { events += "reset" }, onMarkDone = { events += "done" },
                )
            }
        }
    }

    private val ready = FocusUiState(taskTitle = "Write the report")

    @Test
    fun ready_shows_the_task_the_length_and_a_start_button() {
        show(ready)
        compose.onNodeWithTag("focus-task").assertIsDisplayed()
        compose.onNodeWithText("Write the report").assertIsDisplayed()
        compose.onNodeWithTag("focus-clock").assertIsDisplayed()
        compose.onNodeWithText("25:00").assertIsDisplayed()
        compose.onNodeWithText("READY TO FOCUS").assertIsDisplayed()
        compose.onNodeWithTag("focus-start").performClick()
        assertEquals(listOf("start"), events)
    }

    @Test
    fun the_length_can_be_picked_before_starting() {
        show(ready)
        compose.onNodeWithTag("minutes-45").performClick()
        compose.onNodeWithTag("minutes-15").performClick()
        assertEquals(listOf("min:45", "min:15"), events)
    }

    @Test
    fun while_running_you_can_only_pause() {
        show(ready.copy(phase = FocusPhase.RUNNING, remainingSeconds = 1493))
        compose.onNodeWithText("24:53").assertIsDisplayed()
        compose.onNodeWithText("FOCUSING").assertIsDisplayed()
        compose.onNodeWithTag("minutes-45").assertDoesNotExist()
        compose.onNodeWithTag("focus-start").assertDoesNotExist()
        compose.onNodeWithTag("focus-done").assertDoesNotExist()
        compose.onNodeWithTag("focus-pause").performClick()
        assertEquals(listOf("pause"), events)
    }

    @Test
    fun paused_offers_resume_and_reset() {
        show(ready.copy(phase = FocusPhase.PAUSED, remainingSeconds = 600))
        compose.onNodeWithText("PAUSED").assertIsDisplayed()
        compose.onNodeWithTag("focus-resume").performClick()
        compose.onNodeWithTag("focus-reset").performClick()
        assertEquals(listOf("start", "reset"), events)
    }

    @Test
    fun finished_says_so_and_offers_another_round() {
        show(ready.copy(phase = FocusPhase.FINISHED, remainingSeconds = 0))
        compose.onNodeWithText("TIME'S UP").assertIsDisplayed()
        compose.onNodeWithText("00:00").assertIsDisplayed()
        compose.onNodeWithTag("minutes-25").assertIsDisplayed()
        compose.onNodeWithTag("focus-again").performClick()
        assertEquals(listOf("start"), events)
    }

    @Test
    fun the_task_can_be_marked_done_from_here_until_it_is() {
        show(ready)
        compose.onNodeWithTag("focus-done").performClick()
        assertEquals(listOf("done"), events)
    }

    @Test
    fun a_done_task_shows_a_label_instead_of_the_button() {
        show(ready.copy(taskDone = true))
        compose.onNodeWithTag("focus-done").assertDoesNotExist()
        compose.onNodeWithTag("focus-task-done").assertIsDisplayed()
    }

    @Test
    fun a_failure_message_is_shown() {
        show(ready.copy(message = "Couldn't mark the task done"))
        compose.onNodeWithTag("focus-message").assertIsDisplayed()
    }

    @Test
    fun the_back_link_goes_back() {
        show(ready)
        compose.onNodeWithTag("focus-back").performClick()
        assertEquals(listOf("back"), events)
    }

    // ---- the real view-model, with the real clock

    private val tasks = object : TasksRepository {
        var markedDone = false
        override val changes: Flow<Unit> = MutableSharedFlow()
        override suspend fun get(id: String) =
            Result.success(Task(id, "Write the report", "", null, Priority.NORMAL, TaskStatus.TODO, null, null, false, false, null, emptyList()))
        override suspend fun setDone(id: String, done: Boolean): Result<Task> {
            markedDone = true
            return Result.success(Task(id, "Write the report", "", null, Priority.NORMAL, TaskStatus.DONE, Instant.now(), null, false, false, null, emptyList()))
        }
        override suspend fun dueBetween(from: Instant, to: Instant): Result<List<Task>> = error("unused")
        override suspend fun search(query: String): Result<List<Task>> = error("unused")
        override suspend fun all(): Result<List<Task>> = error("unused")
        override suspend fun create(task: NewTask): Result<Task> = error("unused")
        override suspend fun update(id: String, patch: TaskPatch): Result<Task> = error("unused")
        override suspend fun delete(id: String): Result<Unit> = error("unused")
        override suspend fun addSubtask(taskId: String, title: String): Result<Subtask> = error("unused")
        override suspend fun setSubtaskDone(id: String, done: Boolean): Result<Subtask> = error("unused")
    }

    private fun showLive() {
        val vm = FocusViewModel(SavedStateHandle(mapOf("taskId" to "t1")), tasks, Clock.systemUTC())
        compose.setContent {
            PaperTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                FocusScreen(state, {}, vm::setMinutes, vm::start, vm::pause, vm::reset, vm::markTaskDone)
            }
        }
    }

    @Test
    fun the_countdown_really_runs_pauses_and_resets() {
        showLive()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Write the report")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("minutes-15").performClick()
        compose.onNodeWithText("15:00").assertIsDisplayed()

        compose.onNodeWithTag("focus-start").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("15:00")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("FOCUSING").assertIsDisplayed()

        compose.onNodeWithTag("focus-pause").performClick()
        compose.onNodeWithText("PAUSED").assertIsDisplayed()
        val frozen = compose.onNodeWithTag("focus-clock").fetchSemanticsNode().config.toString()
        Thread.sleep(1_300)
        compose.waitForIdle()
        assertEquals(frozen, compose.onNodeWithTag("focus-clock").fetchSemanticsNode().config.toString())

        compose.onNodeWithTag("focus-reset").performClick()
        compose.onNodeWithText("15:00").assertIsDisplayed()
        compose.onNodeWithText("READY TO FOCUS").assertIsDisplayed()
    }

    @Test
    fun marking_the_task_done_goes_through_the_repository() {
        showLive()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Write the report")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("focus-done").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("focus-task-done")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(tasks.markedDone)
    }
}
