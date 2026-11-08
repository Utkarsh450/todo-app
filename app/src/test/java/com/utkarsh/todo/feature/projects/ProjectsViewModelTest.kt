package com.utkarsh.todo.feature.projects

import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectPalette
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.projects.ui.ProjectsViewModel
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-07T05:30:00Z"), ZoneId.of("Asia/Kolkata"))
    private val projects = mockk<ProjectsRepository>()
    private val tasks = mockk<TasksRepository>()
    private val changes = MutableSharedFlow<Unit>()

    private val design = Project("p1", "Design", "#935EDF")
    private val home = Project("p2", "Home", "#2784D5")

    private fun task(id: String, project: String?, done: Boolean = false, due: String? = null) = Task(
        id, "Task $id", "", project, Priority.NORMAL, if (done) TaskStatus.DONE else TaskStatus.TODO,
        due?.let(Instant::parse), null, false, false, null, emptyList(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { tasks.changes } returns changes
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        projectList: List<Project> = listOf(design, home),
        taskList: List<Task> = listOf(task("a", "p1", done = true), task("b", "p1"), task("c", "p2"), task("loose", null)),
    ): ProjectsViewModel {
        coEvery { projects.projects() } returns Result.success(projectList)
        coEvery { tasks.all() } returns Result.success(taskList)
        return ProjectsViewModel(projects, tasks, clock)
    }

    @Test
    fun `groups tasks under their project and counts progress`() {
        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        val d = state.rows.first { it.id == "p1" }
        assertEquals(1, d.done)
        assertEquals(2, d.total)
        assertEquals(0.5f, d.progress, 0.001f)
        assertEquals(3, state.total) // the task without a project is not counted anywhere
        assertEquals(1, state.totalDone)
    }

    @Test
    fun `a project with no tasks has zero progress`() {
        val row = viewModel(taskList = emptyList()).uiState.value.rows.first()
        assertEquals(0f, row.progress, 0f)
    }

    @Test
    fun `open tasks come before done ones and the earliest due comes first`() {
        val vm = viewModel(taskList = listOf(task("done", "p1", done = true), task("late", "p1", due = "2026-11-09T05:30:00Z"), task("soon", "p1", due = "2026-11-08T05:30:00Z")))
        assertEquals(listOf("soon", "late", "done"), vm.uiState.value.rows.first { it.id == "p1" }.tasks.map { it.id })
    }

    @Test
    fun `a failed first load shows an error and retry works`() {
        coEvery { projects.projects() } returns Result.failure(RuntimeException("offline"))
        coEvery { tasks.all() } returns Result.success(emptyList())
        val vm = ProjectsViewModel(projects, tasks, clock)
        assertEquals("offline", vm.uiState.value.error)
        assertTrue(vm.uiState.value.rows.isEmpty())

        coEvery { projects.projects() } returns Result.success(listOf(design))
        vm.load()
        assertNull(vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.rows.size)
    }

    @Test
    fun `a failed refresh keeps what is on screen and shows a short message`() {
        val vm = viewModel()
        coEvery { projects.projects() } returns Result.failure(RuntimeException("offline"))
        vm.load(showSkeleton = false)
        assertEquals(2, vm.uiState.value.rows.size)
        assertNull(vm.uiState.value.error)
        assertEquals("Couldn't refresh", vm.uiState.value.message)
        vm.dismissMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `tapping a project opens it and tapping it again closes it`() {
        val vm = viewModel()
        vm.toggleExpanded("p1")
        assertEquals("p1", vm.uiState.value.expandedId)
        vm.toggleExpanded("p2")
        assertEquals("p2", vm.uiState.value.expandedId)
        vm.toggleExpanded("p2")
        assertNull(vm.uiState.value.expandedId)
    }

    @Test
    fun `ticking a task updates its row and the progress in place`() {
        val vm = viewModel()
        coEvery { tasks.setDone("b", true) } returns Result.success(task("b", "p1", done = true))

        vm.toggleTask("b")

        val row = vm.uiState.value.rows.first { it.id == "p1" }
        assertEquals(2, row.done)
        assertEquals(1f, row.progress, 0f)
        assertTrue(row.tasks.all { it.done })
    }

    @Test
    fun `unticking a done task reopens it`() {
        val vm = viewModel()
        coEvery { tasks.setDone("a", false) } returns Result.success(task("a", "p1"))
        vm.toggleTask("a")
        coVerify { tasks.setDone("a", false) }
        assertEquals(0, vm.uiState.value.rows.first { it.id == "p1" }.done)
    }

    @Test
    fun `a failed tick leaves the row alone and says so`() {
        val vm = viewModel()
        coEvery { tasks.setDone("b", true) } returns Result.failure(RuntimeException("x"))
        vm.toggleTask("b")
        assertEquals(1, vm.uiState.value.rows.first { it.id == "p1" }.done)
        assertEquals("Couldn't update the task", vm.uiState.value.message)
    }

    @Test
    fun `a change made elsewhere reloads quietly`() = kotlinx.coroutines.test.runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(taskList = emptyList())
        assertEquals(0, vm.uiState.value.total)
        coEvery { tasks.all() } returns Result.success(listOf(task("n", "p1")))
        changes.emit(Unit)
        assertEquals(1, vm.uiState.value.total)
        assertFalse(vm.uiState.value.isLoading)
    }

    // ---- editor

    @Test
    fun `a new project starts with the first colour no project uses`() {
        val vm = viewModel()
        vm.openNew()
        val editor = vm.uiState.value.editor!!
        assertTrue(editor.isNew)
        assertEquals(ProjectPalette[2], editor.colorHex) // #935EDF and #2784D5 are taken
    }

    @Test
    fun `saving needs a name, a short one, and one nobody else has`() {
        val vm = viewModel()
        vm.openNew()
        vm.saveEditor()
        assertEquals("Give it a name", vm.uiState.value.editor?.error)

        vm.onEditorName("x".repeat(41))
        assertNull(vm.uiState.value.editor?.error) // typing clears the message
        vm.saveEditor()
        assertEquals("Keep the name under 40 characters", vm.uiState.value.editor?.error)

        vm.onEditorName("  design ")
        vm.saveEditor()
        assertEquals("A project with that name already exists", vm.uiState.value.editor?.error)
        coVerify(exactly = 0) { projects.create(any(), any()) }
    }

    @Test
    fun `creating a project adds it, sorts it in, opens it and closes the sheet`() {
        val vm = viewModel()
        coEvery { projects.create("Art", any()) } returns Result.success(Project("p3", "Art", "#DF911A"))
        vm.openNew()
        vm.onEditorName(" Art ")
        vm.onEditorColor("#DF911A")

        vm.saveEditor()

        val state = vm.uiState.value
        assertNull(state.editor)
        assertEquals(listOf("Art", "Design", "Home"), state.rows.map { it.name })
        assertEquals("p3", state.expandedId)
        coVerify { projects.create("Art", "#DF911A") }
    }

    @Test
    fun `a failed save keeps the sheet open with a message and lets you try again`() {
        val vm = viewModel()
        coEvery { projects.create("Art", any()) } returns Result.failure(RuntimeException("x"))
        vm.openNew()
        vm.onEditorName("Art")
        vm.saveEditor()
        val editor = vm.uiState.value.editor!!
        assertEquals("Couldn't save the project", editor.error)
        assertFalse(editor.saving)
    }

    @Test
    fun `renaming keeps the project's tasks and may keep its own name`() {
        val vm = viewModel()
        coEvery { projects.update("p1", "Design", "#D9548B") } returns Result.success(Project("p1", "Design", "#D9548B"))
        vm.openRename("p1")
        assertEquals("Design", vm.uiState.value.editor?.name)
        vm.onEditorColor("#D9548B") // same name is fine: only the colour changes

        vm.saveEditor()

        val row = vm.uiState.value.rows.first { it.id == "p1" }
        assertEquals("#D9548B", row.colorHex)
        assertEquals(2, row.total)
        assertNull(vm.uiState.value.editor)
    }

    @Test
    fun `closing the editor discards it`() {
        val vm = viewModel()
        vm.openNew()
        vm.closeEditor()
        assertNull(vm.uiState.value.editor)
    }

    // ---- delete

    @Test
    fun `deleting asks first and cancel changes nothing`() {
        val vm = viewModel()
        vm.askDelete("p1")
        assertEquals("p1", vm.uiState.value.confirmDelete?.id)
        vm.cancelDelete()
        assertNull(vm.uiState.value.confirmDelete)
        assertEquals(2, vm.uiState.value.rows.size)
        coVerify(exactly = 0) { projects.delete(any()) }
    }

    @Test
    fun `confirming removes the project and collapses it`() {
        val vm = viewModel()
        coEvery { projects.delete("p1") } returns Result.success(Unit)
        vm.toggleExpanded("p1")
        vm.askDelete("p1")

        vm.confirmDelete()

        assertEquals(listOf("p2"), vm.uiState.value.rows.map { it.id })
        assertNull(vm.uiState.value.expandedId)
        assertNull(vm.uiState.value.confirmDelete)
    }

    @Test
    fun `a failed delete keeps the project and says so`() {
        val vm = viewModel()
        coEvery { projects.delete("p1") } returns Result.failure(RuntimeException("x"))
        vm.askDelete("p1")
        vm.confirmDelete()
        assertEquals(2, vm.uiState.value.rows.size)
        assertNotNull(vm.uiState.value.message)
        assertNull(vm.uiState.value.confirmDelete)
    }
}
