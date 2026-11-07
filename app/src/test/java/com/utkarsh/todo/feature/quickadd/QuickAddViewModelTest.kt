package com.utkarsh.todo.feature.quickadd

import app.cash.turbine.test
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.quickadd.domain.QuickAddParser
import com.utkarsh.todo.feature.quickadd.ui.QuickAddViewModel
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickAddViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), zone) // Sat 10:00 IST
    private val tasks = mockk<TasksRepository>()
    private val projects = mockk<ProjectsRepository>()

    private fun created(n: NewTask) = Task("t1", n.title, "", n.projectId, n.priority, TaskStatus.TODO, n.dueAt, null, false, false, n.repeatRule, emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { projects.projects() } returns Result.success(listOf(Project("p-design", "Design", "#935EDF")))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = QuickAddViewModel(tasks, projects, QuickAddParser(clock), clock)

    @Test
    fun `typing parses tokens and enables submit only with a title`() {
        val vm = viewModel()
        assertFalse(vm.uiState.value.canSubmit)

        vm.onTextChange("Review tokens tomorrow 9am #Design !high")
        assertTrue(vm.uiState.value.canSubmit)
        assertEquals(3, vm.uiState.value.parsed?.tokens?.size)

        vm.onTextChange("tomorrow 9am") // only tokens, no title
        assertFalse(vm.uiState.value.canSubmit)

        vm.onTextChange("   ")
        assertNull(vm.uiState.value.parsed)
    }

    @Test
    fun `submit creates the task resolving project priority and due time`() = runTest {
        val slot = slot<NewTask>()
        coEvery { tasks.create(capture(slot)) } answers { Result.success(created(firstArg())) }
        val vm = viewModel()
        vm.onTextChange("Review tokens tomorrow 9am #design !high every friday")

        vm.added.test {
            vm.submit()
            awaitItem()
        }

        assertEquals("Review tokens", slot.captured.title)
        assertEquals("p-design", slot.captured.projectId) // case-insensitive match
        assertEquals(Priority.HIGH, slot.captured.priority)
        assertEquals(Instant.parse("2026-11-08T03:30:00Z"), slot.captured.dueAt) // tomorrow 9:00 IST
        assertEquals("Every Friday", slot.captured.repeatRule)
        assertEquals("", vm.uiState.value.text) // reset for next time
    }

    @Test
    fun `a task with no date lands on today at the next full hour`() = runTest {
        val slot = slot<NewTask>()
        coEvery { tasks.create(capture(slot)) } answers { Result.success(created(firstArg())) }
        val vm = viewModel()
        vm.onTextChange("Buy oat milk")

        vm.submit()

        assertEquals(Instant.parse("2026-11-07T05:30:00Z"), slot.captured.dueAt) // 11:00 IST
        assertEquals(Priority.NORMAL, slot.captured.priority)
        assertNull(slot.captured.projectId)
    }

    @Test
    fun `an unknown project name is ignored instead of failing`() = runTest {
        val slot = slot<NewTask>()
        coEvery { tasks.create(capture(slot)) } answers { Result.success(created(firstArg())) }
        val vm = viewModel()
        vm.onTextChange("Plan trip #Travel")

        vm.submit()

        assertNull(slot.captured.projectId)
    }

    @Test
    fun `a failed create keeps the text and shows the error`() = runTest {
        coEvery { tasks.create(any()) } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel()
        vm.onTextChange("Buy oat milk")

        vm.submit()

        assertEquals("offline", vm.uiState.value.error)
        assertEquals("Buy oat milk", vm.uiState.value.text)
        assertFalse(vm.uiState.value.isSaving)
        vm.onTextChange("Buy oat milk now") // editing clears the error
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `submit with nothing typed does not call the repository`() {
        viewModel().submit()
        coVerify(exactly = 0) { tasks.create(any()) }
    }
}
