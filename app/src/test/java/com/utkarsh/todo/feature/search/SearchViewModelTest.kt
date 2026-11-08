package com.utkarsh.todo.feature.search

import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.search.ui.SearchPhase
import com.utkarsh.todo.feature.search.ui.SearchViewModel
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-07T05:30:00Z"), ZoneId.of("Asia/Kolkata")) // Sat 7 Nov 11:00 IST
    private val tasks = mockk<TasksRepository>()
    private val projects = mockk<ProjectsRepository>()
    private val changes = MutableSharedFlow<Unit>()
    private val dispatcher = StandardTestDispatcher()

    private fun task(id: String, title: String, done: Boolean = false, project: String? = null, due: String? = null) = Task(
        id, title, "", project, Priority.NORMAL, if (done) TaskStatus.DONE else TaskStatus.TODO,
        due?.let(Instant::parse), null, false, false, null, emptyList(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { tasks.changes } returns changes
        coEvery { projects.projects() } returns Result.success(listOf(Project("p1", "Design", "#935EDF")))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = SearchViewModel(tasks, projects, clock).also { runCurrent() }

    @Test
    fun `starts idle and asks nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(SearchPhase.IDLE, vm.uiState.value.phase)
        coVerify(exactly = 0) { tasks.search(any()) }
    }

    @Test
    fun `waits for a pause in typing and then searches once`() = runTest(dispatcher) {
        coEvery { tasks.search("inv") } returns Result.success(listOf(task("a", "Invoice Acme")))
        val vm = viewModel()

        vm.onQueryChange("i"); advanceTimeBy(100)
        vm.onQueryChange("in"); advanceTimeBy(100)
        vm.onQueryChange("inv")
        assertEquals(SearchPhase.LOADING, vm.uiState.value.phase)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS - 1); runCurrent()
        coVerify(exactly = 0) { tasks.search(any()) }
        advanceTimeBy(2); runCurrent()

        coVerify(exactly = 1) { tasks.search(any()) }
        assertEquals(SearchPhase.RESULTS, vm.uiState.value.phase)
        assertEquals("Invoice Acme", vm.uiState.value.results.single().title)
    }

    @Test
    fun `no match is a state of its own`() = runTest(dispatcher) {
        coEvery { tasks.search("zzz") } returns Result.success(emptyList())
        val vm = viewModel()
        vm.onQueryChange("zzz"); advanceUntilIdle()
        assertEquals(SearchPhase.EMPTY, vm.uiState.value.phase)
    }

    @Test
    fun `the query is trimmed before it is sent and a blank one searches nothing`() = runTest(dispatcher) {
        coEvery { tasks.search("plan") } returns Result.success(listOf(task("a", "Plan trip")))
        val vm = viewModel()
        vm.onQueryChange("  plan  "); advanceUntilIdle()
        coVerify { tasks.search("plan") }

        vm.onQueryChange("   "); advanceUntilIdle()
        assertEquals(SearchPhase.IDLE, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.results.isEmpty())
        coVerify(exactly = 1) { tasks.search(any()) }
    }

    @Test
    fun `clearing the box goes back to idle and drops a search that was waiting`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onQueryChange("abc")
        vm.clear(); advanceUntilIdle()
        assertEquals("", vm.uiState.value.query)
        assertEquals(SearchPhase.IDLE, vm.uiState.value.phase)
        coVerify(exactly = 0) { tasks.search(any()) }
    }

    @Test
    fun `a slow answer to an old question never replaces a newer one`() = runTest(dispatcher) {
        val slow = CompletableDeferred<Result<List<Task>>>()
        coEvery { tasks.search("old") } coAnswers { slow.await() }
        coEvery { tasks.search("new") } returns Result.success(listOf(task("n", "New thing")))
        val vm = viewModel()

        vm.onQueryChange("old"); advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1); runCurrent()
        vm.onQueryChange("new"); advanceUntilIdle()
        slow.complete(Result.success(listOf(task("o", "Old thing"))))
        advanceUntilIdle()

        assertEquals(listOf("n"), vm.uiState.value.results.map { it.id })
    }

    @Test
    fun `open tasks come before done ones, with project and due labels`() = runTest(dispatcher) {
        coEvery { tasks.search("a") } returns Result.success(
            listOf(task("d", "Done one", done = true), task("o", "Open one", project = "p1", due = "2026-11-08T05:30:00Z"), task("x", "Open two", project = "gone")),
        )
        val vm = viewModel()
        vm.onQueryChange("a"); advanceUntilIdle()

        val rows = vm.uiState.value.results
        assertEquals(listOf("o", "x", "d"), rows.map { it.id })
        assertEquals("Design", rows[0].projectName)
        assertEquals("Tomorrow, 11:00 AM", rows[0].dueLabel)
        assertNull(rows[1].projectName) // a project we do not know is simply not shown
        assertNull(rows[1].dueLabel)
        assertTrue(rows[2].done)
    }

    @Test
    fun `a failure shows an error and try again searches again`() = runTest(dispatcher) {
        coEvery { tasks.search("a") } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel()
        vm.onQueryChange("a"); advanceUntilIdle()
        assertEquals(SearchPhase.ERROR, vm.uiState.value.phase)
        assertEquals("offline", vm.uiState.value.error)

        coEvery { tasks.search("a") } returns Result.success(listOf(task("a", "Apple")))
        vm.retry(); advanceUntilIdle()
        assertEquals(SearchPhase.RESULTS, vm.uiState.value.phase)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `a task changed elsewhere refreshes the results quietly`() = runTest(dispatcher) {
        coEvery { tasks.search("a") } returns Result.success(listOf(task("a", "Apple")))
        val vm = viewModel()
        vm.onQueryChange("a"); advanceUntilIdle()

        coEvery { tasks.search("a") } returns Result.success(listOf(task("a", "Apple", done = true)))
        changes.emit(Unit); advanceUntilIdle()

        assertTrue(vm.uiState.value.results.single().done)
        assertEquals(SearchPhase.RESULTS, vm.uiState.value.phase)
    }

    @Test
    fun `a failed quiet refresh keeps the results already shown`() = runTest(dispatcher) {
        coEvery { tasks.search("a") } returns Result.success(listOf(task("a", "Apple")))
        val vm = viewModel()
        vm.onQueryChange("a"); advanceUntilIdle()

        coEvery { tasks.search("a") } returns Result.failure(RuntimeException("x"))
        changes.emit(Unit); advanceUntilIdle()

        assertEquals(SearchPhase.RESULTS, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.results.size)
    }

    @Test
    fun `an over-long query is cut to the limit`() = runTest(dispatcher) {
        coEvery { tasks.search(any()) } returns Result.success(emptyList())
        val vm = viewModel()
        vm.onQueryChange("x".repeat(200))
        assertEquals(SearchViewModel.MAX_QUERY, vm.uiState.value.query.length)
    }
}
