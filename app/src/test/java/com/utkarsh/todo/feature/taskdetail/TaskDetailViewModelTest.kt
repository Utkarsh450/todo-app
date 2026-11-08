package com.utkarsh.todo.feature.taskdetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.utkarsh.todo.core.common.formatRelativeDue
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.taskdetail.ui.TaskDetailViewModel
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Subtask
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), zone) // Sat 7 Nov 10:00 IST
    private val tasks = mockk<TasksRepository>()
    private val projects = mockk<ProjectsRepository>()
    private val reminders = mockk<RemindersRepository>(relaxed = true)

    private val base = Task(
        id = "t1", title = "Ship onboarding redesign", notes = "Keep it to three screens.", projectId = "p1",
        priority = Priority.URGENT, status = TaskStatus.IN_PROGRESS, dueAt = Instant.parse("2026-11-07T08:30:00Z"),
        completedAt = null, archived = false, pinned = true, repeatRule = "Every Friday",
        subtasks = listOf(Subtask("s1", "t1", "Audit", true), Subtask("s2", "t1", "Wireframe", false)),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { projects.projects() } returns Result.success(listOf(Project("p1", "Design", "#935EDF")))
        coEvery { tasks.get("t1") } returns Result.success(base)
        coEvery { reminders.forTask("t1") } returns Result.success(null)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TaskDetailViewModel(SavedStateHandle(mapOf("taskId" to "t1")), tasks, projects, reminders, clock)

    @Test
    fun `loads and maps the task for display`() {
        val ui = viewModel().uiState.value.task!!

        assertEquals("IN PROGRESS", ui.statusLabel)
        assertEquals("Today, 2:00 PM", ui.dueLabel)
        assertEquals("Design", ui.projectName)
        assertEquals("Every Friday", ui.repeat)
        assertEquals(1, ui.subtasksDone)
        assertEquals(false, ui.done)
    }

    @Test
    fun `load failure is reported`() {
        coEvery { tasks.get("t1") } returns Result.failure(RuntimeException("offline"))
        val state = viewModel().uiState.value
        assertNull(state.task)
        assertEquals("offline", state.error)
    }

    @Test
    fun `mark done updates the displayed status`() {
        coEvery { tasks.setDone("t1", true) } returns Result.success(base.copy(status = TaskStatus.DONE))
        val vm = viewModel()

        vm.toggleDone()

        assertEquals(true, vm.uiState.value.task!!.done)
        assertEquals("DONE", vm.uiState.value.task!!.statusLabel)
    }

    @Test
    fun `toggling a subtask replaces just that subtask`() {
        coEvery { tasks.setSubtaskDone("s2", true) } returns Result.success(Subtask("s2", "t1", "Wireframe", true))
        val vm = viewModel()

        vm.toggleSubtask("s2")

        assertEquals(2, vm.uiState.value.task!!.subtasksDone)
    }

    @Test
    fun `adding a subtask trims input and appends it`() {
        coEvery { tasks.addSubtask("t1", "Write copy") } returns Result.success(Subtask("s3", "t1", "Write copy", false))
        val vm = viewModel()

        vm.addSubtask("  Write copy ")
        vm.addSubtask("   ") // ignored

        assertEquals(listOf("s1", "s2", "s3"), vm.uiState.value.task!!.subtasks.map { it.id })
        coVerify(exactly = 1) { tasks.addSubtask(any(), any()) }
    }

    @Test
    fun `refresh picks up new subtasks without showing the spinner`() {
        val vm = viewModel()
        val withNew = base.copy(subtasks = base.subtasks + Subtask("s3", "t1", "Pack", false))
        coEvery { tasks.get("t1") } returns Result.success(withNew)

        vm.refresh()

        assertEquals(listOf("s1", "s2", "s3"), vm.uiState.value.task!!.subtasks.map { it.id })
        assertTrue(!vm.uiState.value.isLoading)
    }

    @Test
    fun `a failed refresh keeps what is on screen`() {
        val vm = viewModel()
        coEvery { tasks.get("t1") } returns Result.failure(RuntimeException("offline"))

        vm.refresh()

        assertEquals(2, vm.uiState.value.task!!.subtasks.size)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `delete closes the screen on success`() = runTest {
        coEvery { tasks.delete("t1") } returns Result.success(Unit)
        val vm = viewModel()

        vm.closed.test {
            vm.delete()
            awaitItem()
        }
    }

    @Test
    fun `delete failure keeps the screen open with an error`() {
        coEvery { tasks.delete("t1") } returns Result.failure(RuntimeException("x"))
        val vm = viewModel()

        vm.delete()

        assertEquals("Couldn't delete task", vm.uiState.value.error)
        assertTrue(vm.uiState.value.task != null)
    }

    @Test
    fun `relative due labels cover yesterday tomorrow and far dates`() {
        val today = LocalDate.of(2026, 11, 7)
        assertEquals("Yesterday, 9:00 AM", Instant.parse("2026-11-06T03:30:00Z").formatRelativeDue(zone, today))
        assertEquals("Tomorrow, 9:00 AM", Instant.parse("2026-11-08T03:30:00Z").formatRelativeDue(zone, today))
        assertEquals("Mon 9 Nov, 9:00 AM", Instant.parse("2026-11-09T03:30:00Z").formatRelativeDue(zone, today))
    }
}
