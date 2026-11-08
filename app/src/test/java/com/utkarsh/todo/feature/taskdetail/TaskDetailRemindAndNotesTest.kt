package com.utkarsh.todo.feature.taskdetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.taskdetail.ui.RemindState
import com.utkarsh.todo.feature.taskdetail.ui.TaskDetailViewModel
import com.utkarsh.todo.feature.tasks.domain.Field
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailRemindAndNotesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), zone) // Sat 7 Nov, 10:00 IST
    private val tasks = mockk<TasksRepository>()
    private val projects = mockk<ProjectsRepository>()
    private val reminders = mockk<RemindersRepository>()

    private fun task(due: String? = "2026-11-07T08:30:00Z", notes: String = "") = Task(
        id = "t1", title = "Ship onboarding", notes = notes, projectId = null, priority = Priority.NORMAL, status = TaskStatus.TODO,
        dueAt = due?.let(Instant::parse), completedAt = null, archived = false, pinned = false, repeatRule = null, subtasks = emptyList(),
    )

    private fun reminder(at: String = "2026-11-07T07:30:00Z") = Reminder("r1", "t1", "Ship onboarding", Instant.parse(at), null, null, true, null)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        coEvery { projects.projects() } returns Result.success(emptyList())
        coEvery { tasks.get("t1") } returns Result.success(task())
        coEvery { reminders.forTask("t1") } returns Result.success(null)
        every { reminders.forgetAlarm(any()) } returns Unit
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TaskDetailViewModel(SavedStateHandle(mapOf("taskId" to "t1")), tasks, projects, reminders, clock)

    // ---- the REMIND row

    @Test
    fun `a task without a due date cannot be reminded about`() = runTest {
        coEvery { tasks.get("t1") } returns Result.success(task(due = null))
        val vm = viewModel(); runCurrent()
        assertEquals(RemindState.NEEDS_DUE, vm.uiState.value.task!!.remind.state)
    }

    @Test
    fun `a task due in more than an hour offers the reminder`() = runTest {
        val vm = viewModel(); runCurrent()
        val remind = vm.uiState.value.task!!.remind
        assertEquals(RemindState.OFF, remind.state)
        assertEquals("+ 1h before due", remind.label)
    }

    @Test
    fun `a task due within the hour or overdue is too soon`() = runTest {
        coEvery { tasks.get("t1") } returns Result.success(task(due = "2026-11-07T05:00:00Z"))
        val vm = viewModel(); runCurrent()
        assertEquals(RemindState.TOO_SOON, vm.uiState.value.task!!.remind.state)
    }

    @Test
    fun `an existing reminder shows as on with its time`() = runTest {
        coEvery { reminders.forTask("t1") } returns Result.success(reminder())
        val vm = viewModel(); runCurrent()
        val remind = vm.uiState.value.task!!.remind
        assertEquals(RemindState.ON, remind.state)
        assertEquals("⏰ Today, 1:00 PM", remind.label)
    }

    @Test
    fun `turning it on creates a reminder one hour before the due time for this task`() = runTest {
        coEvery { reminders.create(any()) } returns Result.success(reminder())
        val vm = viewModel(); runCurrent()

        vm.toggleRemind(); runCurrent()

        coVerify(exactly = 1) {
            reminders.create(NewReminder(title = "Ship onboarding", remindAt = Instant.parse("2026-11-07T07:30:00Z"), taskId = "t1"))
        }
        assertEquals(RemindState.ON, vm.uiState.value.task!!.remind.state)
    }

    @Test
    fun `turning it off deletes the reminder`() = runTest {
        coEvery { reminders.forTask("t1") } returns Result.success(reminder())
        coEvery { reminders.delete("r1") } returns Result.success(Unit)
        val vm = viewModel(); runCurrent()

        vm.toggleRemind(); runCurrent()

        coVerify(exactly = 1) { reminders.delete("r1") }
        assertEquals(RemindState.OFF, vm.uiState.value.task!!.remind.state)
    }

    @Test
    fun `a failed reminder change keeps the row as it was and says so`() = runTest {
        coEvery { reminders.create(any()) } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel(); runCurrent()

        vm.toggleRemind(); runCurrent()

        assertEquals("Couldn't set the reminder", vm.uiState.value.error)
        assertEquals(RemindState.OFF, vm.uiState.value.task!!.remind.state)
    }

    @Test
    fun `tapping the row when it is not actionable does nothing`() = runTest {
        coEvery { tasks.get("t1") } returns Result.success(task(due = null))
        val vm = viewModel(); runCurrent()
        vm.toggleRemind(); runCurrent()
        coVerify(exactly = 0) { reminders.create(any()) }
        coVerify(exactly = 0) { reminders.delete(any()) }
    }

    @Test
    fun `an unreachable reminder lookup does not stop the page loading`() = runTest {
        coEvery { reminders.forTask("t1") } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel(); runCurrent()
        assertNotNull(vm.uiState.value.task)
        assertEquals(RemindState.OFF, vm.uiState.value.task!!.remind.state)
    }

    // ---- notes

    @Test
    fun `typing shows at once and is saved once after the pause with the latest text`() = runTest {
        coEvery { tasks.update("t1", any()) } returns Result.success(task(notes = "final"))
        val vm = viewModel(); runCurrent()

        vm.onNotesChange("f"); advanceTimeBy(300)
        vm.onNotesChange("fi"); advanceTimeBy(300)
        vm.onNotesChange("final")
        assertEquals("final", vm.uiState.value.task!!.notes)
        coVerify(exactly = 0) { tasks.update(any(), any()) }

        advanceTimeBy(TaskDetailViewModel.NOTES_SAVE_DELAY_MS + 1); runCurrent()

        coVerify(exactly = 1) { tasks.update("t1", TaskPatch(notes = Field.Set("final"))) }
    }

    @Test
    fun `a failed save is reported`() = runTest {
        coEvery { tasks.update("t1", any()) } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel(); runCurrent()

        vm.onNotesChange("x"); advanceTimeBy(TaskDetailViewModel.NOTES_SAVE_DELAY_MS + 1); runCurrent()

        assertEquals("Couldn't save notes", vm.uiState.value.error)
        assertEquals("x", vm.uiState.value.task!!.notes) // what was typed stays
    }

    @Test
    fun `a refresh while notes are waiting to be saved does not undo the typing`() = runTest {
        coEvery { tasks.update("t1", any()) } returns Result.success(task(notes = "typed"))
        val vm = viewModel(); runCurrent()

        vm.onNotesChange("typed")
        vm.refresh(); runCurrent() // the server still has the old (empty) notes

        assertEquals("typed", vm.uiState.value.task!!.notes)
    }

    // ---- delete

    @Test
    fun `deleting the task also drops its device alarm`() = runTest {
        coEvery { reminders.forTask("t1") } returns Result.success(reminder())
        coEvery { tasks.delete("t1") } returns Result.success(Unit)
        val vm = viewModel(); runCurrent()

        vm.closed.test {
            vm.delete(); runCurrent()
            awaitItem()
        }

        verify(exactly = 1) { reminders.forgetAlarm("r1") }
    }

    @Test
    fun `a failed delete keeps the alarm`() = runTest {
        coEvery { reminders.forTask("t1") } returns Result.success(reminder())
        coEvery { tasks.delete("t1") } returns Result.failure(RuntimeException("x"))
        val vm = viewModel(); runCurrent()

        vm.delete(); runCurrent()

        verify(exactly = 0) { reminders.forgetAlarm(any()) }
    }
}
