package com.utkarsh.todo.feature.breakdown

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.utkarsh.todo.feature.ai.domain.AiException
import com.utkarsh.todo.feature.ai.domain.AiRepository
import com.utkarsh.todo.feature.breakdown.ui.BreakdownPhase
import com.utkarsh.todo.feature.breakdown.ui.BreakdownViewModel
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Subtask
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import java.time.Instant
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
class BreakdownViewModelTest {
    private val tasks = mockk<TasksRepository>()
    private val ai = mockk<AiRepository>()
    private val due = Instant.parse("2026-11-10T09:00:00Z")

    private val task = Task(
        id = "t1", title = "Plan trip", notes = "Goa, 4 days", projectId = null, priority = Priority.NORMAL,
        status = TaskStatus.TODO, dueAt = due, completedAt = null, archived = false, pinned = false, repeatRule = null, subtasks = emptyList(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { tasks.get("t1") } returns Result.success(task)
        coEvery { ai.breakDown("Plan trip", "Goa, 4 days", due) } returns Result.success(listOf("Book flights", "Find a hotel", "Pack"))
        coEvery { tasks.addSubtask("t1", any()) } answers { Result.success(Subtask("s-${secondArg<String>()}", "t1", secondArg(), false)) }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = BreakdownViewModel(SavedStateHandle(mapOf("taskId" to "t1")), tasks, ai)

    @Test
    fun `asks about the open task and offers every step ticked`() {
        val state = viewModel().uiState.value

        assertEquals(BreakdownPhase.READY, state.phase)
        assertEquals(listOf("Book flights", "Find a hotel", "Pack"), state.suggestions.map { it.title })
        assertTrue(state.suggestions.all { it.selected })
        assertEquals(3, state.selectedCount)
        coVerify(exactly = 1) { ai.breakDown("Plan trip", "Goa, 4 days", due) }
    }

    @Test
    fun `nothing is added before the person confirms`() {
        viewModel()
        coVerify(exactly = 0) { tasks.addSubtask(any(), any()) }
    }

    @Test
    fun `unticking a step skips it and the count follows`() {
        val vm = viewModel()
        vm.toggle(1)

        assertEquals(2, vm.uiState.value.selectedCount)
        assertFalse(vm.uiState.value.suggestions[1].selected)
        vm.toggle(1)
        assertEquals(3, vm.uiState.value.selectedCount)
    }

    @Test
    fun `toggling an unknown row does nothing`() {
        val vm = viewModel()
        vm.toggle(9)
        vm.toggle(-1)
        assertEquals(3, vm.uiState.value.selectedCount)
    }

    @Test
    fun `adds only the ticked steps, in order, then reports done`() = runTest {
        val vm = viewModel()
        vm.toggle(1)

        vm.added.test {
            vm.addSelected()
            awaitItem()
        }

        coVerifyOrder {
            tasks.addSubtask("t1", "Book flights")
            tasks.addSubtask("t1", "Pack")
        }
        coVerify(exactly = 0) { tasks.addSubtask("t1", "Find a hotel") }
    }

    @Test
    fun `with nothing ticked adding does nothing`() {
        val vm = viewModel()
        vm.toggle(0); vm.toggle(1); vm.toggle(2)

        vm.addSelected()

        coVerify(exactly = 0) { tasks.addSubtask(any(), any()) }
    }

    @Test
    fun `a failure part way keeps only the steps still to add`() = runTest {
        coEvery { tasks.addSubtask("t1", "Find a hotel") } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel()

        vm.addSelected()

        val state = vm.uiState.value
        assertFalse(state.adding)
        assertEquals(listOf("Find a hotel", "Pack"), state.suggestions.map { it.title })
        assertEquals("Added 1 of 3. Couldn't add the rest.", state.message)

        // Retrying must not create "Book flights" a second time.
        coEvery { tasks.addSubtask("t1", "Find a hotel") } answers { Result.success(Subtask("s2", "t1", "Find a hotel", false)) }
        vm.added.test {
            vm.addSelected()
            awaitItem()
        }
        coVerify(exactly = 1) { tasks.addSubtask("t1", "Book flights") }
    }

    @Test
    fun `a server without a key is its own state`() {
        coEvery { ai.breakDown(any(), any(), any()) } returns Result.failure(AiException("AI is not set up.", notConfigured = true))

        val state = viewModel().uiState.value

        assertEquals(BreakdownPhase.NOT_CONFIGURED, state.phase)
        assertTrue(state.suggestions.isEmpty())
    }

    @Test
    fun `an assistant failure shows its message and can be retried`() {
        coEvery { ai.breakDown(any(), any(), any()) } returns Result.failure(AiException("The AI service is busy or over its limit. Try again in a moment."))
        val vm = viewModel()

        assertEquals(BreakdownPhase.ERROR, vm.uiState.value.phase)
        assertEquals("The AI service is busy or over its limit. Try again in a moment.", vm.uiState.value.message)

        coEvery { ai.breakDown(any(), any(), any()) } returns Result.success(listOf("One"))
        vm.suggest()

        assertEquals(BreakdownPhase.READY, vm.uiState.value.phase)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `a task that cannot be loaded never reaches the assistant`() {
        coEvery { tasks.get("t1") } returns Result.failure(RuntimeException("gone"))

        val state = viewModel().uiState.value

        assertEquals(BreakdownPhase.ERROR, state.phase)
        assertEquals("gone", state.message)
        coVerify(exactly = 0) { ai.breakDown(any(), any(), any()) }
    }
}
