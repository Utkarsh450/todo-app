package com.utkarsh.todo.feature.today

import com.utkarsh.todo.core.common.dayRange
import com.utkarsh.todo.core.offline.CacheFirst
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.ReminderGroups
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.tasks.domain.Field
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Subtask
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import com.utkarsh.todo.feature.today.ui.TodayViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private val changes = MutableSharedFlow<Unit>()
    private val tasks = mockk<TasksRepository>()
    private val projects = mockk<ProjectsRepository>()
    private val reminders = mockk<RemindersRepository>()

    private fun alert(id: String, enabled: Boolean = true) =
        Reminder(id, null, "Alert $id", Instant.parse("2026-11-07T08:00:00Z"), null, null, enabled, null)

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
        every { tasks.changes } returns changes
        coEvery { reminders.grouped(any(), any(), any()) } returns Result.success(ReminderGroups())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(vararg initial: Task): TodayViewModel {
        coEvery { tasks.dueBetween(range.start, range.end) } returns Result.success(initial.toList())
        return TodayViewModel(tasks, projects, reminders, clock)
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
        val vm = TodayViewModel(tasks, projects, reminders, clock)
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
    fun `reloads when another screen changes tasks`() = runTest {
        val vm = viewModel(task("a"))
        coEvery { tasks.dueBetween(range.start, range.end) } returns Result.success(listOf(task("a"), task("new", dueIso = "2026-11-07T12:00:00Z")))

        changes.emit(Unit)

        assertEquals(listOf("a", "new"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `the due soon banner counts switched-on overdue and today alerts only`() {
        coEvery { reminders.grouped(any(), any(), any()) } returns Result.success(
            ReminderGroups(
                overdue = listOf(alert("o1"), alert("o2", enabled = false)),
                today = listOf(alert("t1"), alert("t2"), alert("t3", enabled = false)),
            ),
        )

        val summary = viewModel().uiState.value.dueSoon

        assertEquals(1, summary.overdue)
        assertEquals(2, summary.today)
        assertEquals(false, summary.isEmpty)
    }

    @Test
    fun `no alerts means an empty banner and a failing alert lookup never breaks Today`() {
        assertEquals(true, viewModel(task("a")).uiState.value.dueSoon.isEmpty)

        coEvery { reminders.grouped(any(), any(), any()) } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel(task("a"))
        assertEquals(true, vm.uiState.value.dueSoon.isEmpty)
        assertEquals(1, vm.uiState.value.left)
    }

    @Test
    fun `progress counts done tasks of the whole list and the next open task is the first not done`() {
        val state = viewModel(
            task("a", dueIso = "2026-11-07T04:30:00Z", status = TaskStatus.DONE),
            task("b", dueIso = "2026-11-07T06:00:00Z"),
            task("c", dueIso = "2026-11-07T12:00:00Z"),
        ).uiState.value

        assertEquals(3, state.total)
        assertEquals(1, state.doneCount)
        assertEquals(1f / 3f, state.progress, 0.0001f)
        assertEquals("b", state.next?.id)
    }

    @Test
    fun `an empty day has no progress and no next task, and a finished day has no next task`() {
        val empty = viewModel().uiState.value
        assertEquals(0, empty.total)
        assertEquals(0f, empty.progress, 0f)
        assertNull(empty.next)

        val finished = viewModel(task("a", status = TaskStatus.DONE), task("b", status = TaskStatus.DONE)).uiState.value
        assertEquals(1f, finished.progress, 0f)
        assertNull(finished.next)
    }

    // --- saved copy first -------------------------------------------------------------------------------------------

    /** The server answers only when [gate] is completed; a saved copy (asked for with cacheFirst) answers at once. */
    private fun savedThenServer(saved: Result<List<Task>>, gate: CompletableDeferred<Result<List<Task>>>) {
        coEvery { tasks.dueBetween(range.start, range.end) } coAnswers { if (CacheFirst.isActive()) saved else gate.await() }
    }

    @Test
    fun `the saved copy shows at once, with project names, and the server's answer then replaces it`() = runTest {
        val gate = CompletableDeferred<Result<List<Task>>>()
        savedThenServer(Result.success(listOf(task("old", projectId = "p1"))), gate)

        val vm = TodayViewModel(tasks, projects, reminders, clock)

        assertEquals(listOf("old"), vm.uiState.value.items.map { it.id })
        assertEquals("Design", vm.uiState.value.items.single().projectName)
        assertEquals(false, vm.uiState.value.errorMessage != null)

        gate.complete(Result.success(listOf(task("fresh"), task("fresh2", dueIso = "2026-11-07T12:00:00Z"))))

        assertEquals(listOf("fresh", "fresh2"), vm.uiState.value.items.map { it.id })
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test
    fun `a saved empty day is shown as an empty day at once, not as a skeleton`() = runTest {
        val gate = CompletableDeferred<Result<List<Task>>>()
        savedThenServer(Result.success(emptyList()), gate)

        val vm = TodayViewModel(tasks, projects, reminders, clock)

        assertEquals(false, vm.uiState.value.isLoading) // "Nothing planned" right away
        assertEquals(emptyList<String>(), vm.uiState.value.items.map { it.id })

        gate.complete(Result.success(listOf(task("fresh"))))
        assertEquals(listOf("fresh"), vm.uiState.value.items.map { it.id })
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test
    fun `the saved due soon banner shows at once too`() = runTest {
        val gate = CompletableDeferred<Result<ReminderGroups>>()
        coEvery { reminders.grouped(any(), any(), any()) } coAnswers {
            if (CacheFirst.isActive()) Result.success(ReminderGroups(overdue = listOf(alert("o1"), alert("o2")), today = listOf(alert("t1")))) else gate.await()
        }
        coEvery { tasks.dueBetween(range.start, range.end) } returns Result.success(listOf(task("a")))

        val vm = TodayViewModel(tasks, projects, reminders, clock)

        assertEquals(2 to 1, vm.uiState.value.dueSoon.overdue to vm.uiState.value.dueSoon.today)

        gate.complete(Result.success(ReminderGroups(overdue = emptyList(), today = listOf(alert("t1")))))
        assertEquals(0 to 1, vm.uiState.value.dueSoon.overdue to vm.uiState.value.dueSoon.today)
    }

    @Test
    fun `with nothing saved Today keeps loading until the server answers`() = runTest {
        val gate = CompletableDeferred<Result<List<Task>>>()
        savedThenServer(Result.failure(RuntimeException("HTTP 504")), gate)

        val vm = TodayViewModel(tasks, projects, reminders, clock)

        assertEquals(true, vm.uiState.value.isLoading)
        assertEquals(emptyList<String>(), vm.uiState.value.items.map { it.id })
        assertNull(vm.uiState.value.errorMessage) // a missing saved copy is not an error

        gate.complete(Result.success(listOf(task("fresh"))))
        assertEquals(listOf("fresh"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `if the server then fails the saved rows stay and the error is reported`() = runTest {
        val gate = CompletableDeferred<Result<List<Task>>>()
        savedThenServer(Result.success(listOf(task("old"))), gate)
        val vm = TodayViewModel(tasks, projects, reminders, clock)

        gate.complete(Result.failure(RuntimeException("timeout")))

        assertEquals(listOf("old"), vm.uiState.value.items.map { it.id })
        assertEquals("timeout", vm.uiState.value.errorMessage)
    }

    @Test
    fun `a later reload goes straight to the server and never replays an older saved copy`() = runTest {
        val modes = mutableListOf<Boolean>()
        coEvery { tasks.dueBetween(range.start, range.end) } coAnswers {
            modes += CacheFirst.isActive()
            Result.success(listOf(task("a")))
        }
        val vm = TodayViewModel(tasks, projects, reminders, clock)
        assertEquals(listOf(true, false), modes) // first load: saved copy, then the server

        vm.refresh()
        changes.emit(Unit)

        assertEquals(listOf(true, false, false, false), modes)
    }
}
