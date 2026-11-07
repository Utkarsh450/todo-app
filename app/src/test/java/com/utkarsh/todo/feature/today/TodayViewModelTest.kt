package com.utkarsh.todo.feature.today

import com.utkarsh.todo.core.common.dayRange
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.tasks.domain.Field
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Subtask
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import com.utkarsh.todo.feature.today.domain.TodayView
import com.utkarsh.todo.feature.today.ui.TodayViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), zone) // Sat 7 Nov, 10:00 IST
    private val range = clock.dayRange()

    private val tasks = mockk<TasksRepository>()
    private val projects = mockk<ProjectsRepository>()

    private fun task(
        id: String,
        title: String = "Task $id",
        dueIso: String = "2026-11-07T06:00:00Z", // 11:30 IST
        status: TaskStatus = TaskStatus.TODO,
        pinned: Boolean = false,
        projectId: String? = null,
        subtasks: List<Subtask> = emptyList(),
    ) = Task(id, title, "", projectId, Priority.NORMAL, status, Instant.parse(dueIso), null, false, pinned, null, subtasks)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { projects.projects() } returns Result.success(listOf(Project("p1", "Design", "#935EDF")))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(vararg initial: Task): TodayViewModel {
        coEvery { tasks.dueBetween(range.start, range.end) } returns Result.success(initial.toList())
        return TodayViewModel(tasks, projects, clock)
    }

    @Test
    fun `loads today's tasks with formatted time project and counts`() {
        val vm = viewModel(
            task("a", title = "Ship redesign", pinned = true, projectId = "p1", subtasks = listOf(Subtask("s1", "a", "x", true), Subtask("s2", "a", "y", false))),
            task("b", status = TaskStatus.DONE),
        )

        val state = vm.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("SAT 7 NOV · W45", state.caption)
        assertEquals(1, state.left)
        val first = state.items.first()
        assertEquals("11:30 AM", first.timeLabel)
        assertEquals("Design", first.projectName)
        assertEquals("#935EDF", first.projectColorHex)
        assertEquals(1 to 2, first.subtasksDone to first.subtasksTotal)
        assertEquals("a", state.pinned?.id)
    }

    @Test
    fun `a done pinned task is no longer shown as the pinned card`() {
        assertNull(viewModel(task("a", pinned = true, status = TaskStatus.DONE)).uiState.value.pinned)
    }

    @Test
    fun `load failure exposes an error and retry recovers`() = runTest {
        coEvery { tasks.dueBetween(range.start, range.end) } returns Result.failure(RuntimeException("offline"))
        val vm = TodayViewModel(tasks, projects, clock)
        assertEquals("offline", vm.uiState.value.errorMessage)

        coEvery { tasks.dueBetween(range.start, range.end) } returns Result.success(listOf(task("a")))
        vm.refresh()

        assertNull(vm.uiState.value.errorMessage)
        assertEquals(1, vm.uiState.value.items.size)
    }

    @Test
    fun `toggling completes a task optimistically and persists it`() {
        coEvery { tasks.setDone("a", true) } returns Result.success(task("a", status = TaskStatus.DONE))
        val vm = viewModel(task("a"))

        vm.toggleDone("a")

        assertEquals(true, vm.uiState.value.items.single().done)
        assertEquals(0, vm.uiState.value.left)
        coVerify { tasks.setDone("a", true) }
    }

    @Test
    fun `a failed toggle reports an error and reloads from the server`() {
        coEvery { tasks.setDone("a", true) } returns Result.failure(RuntimeException("boom"))
        val vm = viewModel(task("a"))

        vm.toggleDone("a")

        assertEquals("Couldn't update task", vm.uiState.value.errorMessage)
        assertEquals(false, vm.uiState.value.items.single().done) // reloaded truth
    }

    @Test
    fun `moving to tomorrow removes the row, shows undo and shifts due by a day`() {
        val patch = slot<TaskPatch>()
        coEvery { tasks.update("a", capture(patch)) } returns Result.success(task("a", dueIso = "2026-11-08T06:00:00Z"))
        val vm = viewModel(task("a"), task("b"))

        vm.moveToTomorrow("a")

        assertEquals(listOf("b"), vm.uiState.value.items.map { it.id })
        assertEquals("Moved to tomorrow", vm.uiState.value.undo?.message)
        assertEquals(Field.Set(Instant.parse("2026-11-08T06:00:00Z")), patch.captured.dueAt)
    }

    @Test
    fun `undoing a move restores the original due time and the row`() {
        coEvery { tasks.update("a", any()) } answers {
            val p = secondArg<TaskPatch>()
            val due = (p.dueAt as Field.Set).value
            Result.success(task("a", dueIso = due.toString()))
        }
        val vm = viewModel(task("a"), task("b", dueIso = "2026-11-07T12:00:00Z"))
        vm.moveToTomorrow("a")

        vm.undo()

        assertNull(vm.uiState.value.undo)
        assertEquals(listOf("a", "b"), vm.uiState.value.items.map { it.id })
        coVerify { tasks.update("a", TaskPatch(dueAt = Field.Set(Instant.parse("2026-11-07T06:00:00Z")))) }
    }

    @Test
    fun `archiving then undoing unarchives and brings the task back`() {
        coEvery { tasks.update("a", TaskPatch(archived = Field.Set(true))) } returns Result.success(task("a").copy(archived = true))
        coEvery { tasks.update("a", TaskPatch(archived = Field.Set(false))) } returns Result.success(task("a"))
        val vm = viewModel(task("a"))

        vm.archive("a")
        assertEquals(emptyList<String>(), vm.uiState.value.items.map { it.id })
        assertEquals("Archived", vm.uiState.value.undo?.message)

        vm.undo()
        assertEquals(listOf("a"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `dismissing the undo prompt forgets the pending action`() {
        coEvery { tasks.update(any(), any()) } returns Result.success(task("a").copy(archived = true))
        val vm = viewModel(task("a"))
        vm.archive("a")
        assertNotNull(vm.uiState.value.undo)

        vm.dismissUndo()
        vm.undo() // nothing pending: must be a no-op

        assertNull(vm.uiState.value.undo)
        assertEquals(emptyList<String>(), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `switching view keeps data`() {
        val vm = viewModel(task("a"))
        vm.setView(TodayView.PLAN)
        assertEquals(TodayView.PLAN, vm.uiState.value.view)
        assertEquals(1, vm.uiState.value.items.size)
    }
}
