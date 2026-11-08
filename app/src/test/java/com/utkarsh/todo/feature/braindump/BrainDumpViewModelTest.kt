package com.utkarsh.todo.feature.braindump

import app.cash.turbine.test
import com.utkarsh.todo.feature.ai.domain.AiException
import com.utkarsh.todo.feature.ai.domain.AiRepository
import com.utkarsh.todo.feature.ai.domain.DumpedTask
import com.utkarsh.todo.feature.braindump.ui.BrainDumpViewModel
import com.utkarsh.todo.feature.braindump.ui.DumpPhase
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
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
class BrainDumpViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-08T04:30:00Z"), zone) // Sun 8 Nov, 10:00 IST
    private val ai = mockk<AiRepository>()
    private val tasks = mockk<TasksRepository>()
    private val projects = mockk<ProjectsRepository>()

    private val email = DumpedTask("Email Priya the deck", Instant.parse("2026-11-13T04:30:00Z"), Priority.NORMAL, null, "p-design", "Design") // Fri 10:00 IST
    private val crash = DumpedTask("Fix the rotation crash", null, Priority.URGENT, null, null, null)
    private val gym = DumpedTask("Renew the gym", null, Priority.NORMAL, "Every month", null, null)

    private fun task(id: String = "t") = Task(id, "t", "", null, Priority.NORMAL, TaskStatus.TODO, null, null, false, false, null, emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { ai.brainDump(any(), any()) } returns Result.success(listOf(email, crash, gym))
        coEvery { projects.projects() } returns Result.success(listOf(Project("p-design", "Design", "#935EDF")))
        coEvery { tasks.create(any()) } returns Result.success(task())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = BrainDumpViewModel(ai, tasks, projects, clock)

    private fun BrainDumpViewModel.dump(text: String = "Okay so I need to email Priya...") {
        onTextChange(text)
        sort()
    }

    // ---- typing and dictating

    @Test
    fun `dictated words are added after what is already there`() {
        val vm = viewModel()
        vm.appendSpoken("  first thought ")
        vm.appendSpoken("second thought")
        vm.appendSpoken("   ")
        assertEquals("first thought second thought", vm.uiState.value.text)
    }

    @Test
    fun `a blank note is never sent`() {
        val vm = viewModel()
        vm.onTextChange("   ")
        vm.sort()
        assertEquals(DumpPhase.INPUT, vm.uiState.value.phase)
        coVerify(exactly = 0) { ai.brainDump(any(), any()) }
    }

    // ---- sorting

    @Test
    fun `sorting shows each task with its time, priority, repeat and project`() {
        val vm = viewModel()
        vm.dump("  Okay so I need to email Priya...  ")

        val state = vm.uiState.value
        assertEquals(DumpPhase.RESULTS, state.phase)
        assertEquals(listOf("Email Priya the deck", "Fix the rotation crash", "Renew the gym"), state.rows.map { it.title })
        assertEquals("Fri 13 Nov, 10:00 AM", state.rows[0].dueLabel)
        assertEquals("Design", state.rows[0].projectName)
        assertEquals("#935EDF", state.rows[0].projectColorHex)
        assertNull(state.rows[1].dueLabel)
        assertEquals(Priority.URGENT, state.rows[1].priority)
        assertEquals("Every month", state.rows[2].repeat)
        assertTrue(state.rows.all { it.selected })
        assertEquals(3, state.selectedCount)
        coVerify(exactly = 1) { ai.brainDump("Okay so I need to email Priya...", zone) }
    }

    @Test
    fun `nothing is created until the person confirms`() {
        viewModel().dump()
        coVerify(exactly = 0) { tasks.create(any()) }
    }

    @Test
    fun `sorting twice at once asks once`() {
        val gate = CompletableDeferred<Result<List<DumpedTask>>>()
        coEvery { ai.brainDump(any(), any()) } coAnswers { gate.await() }
        val vm = viewModel()
        vm.onTextChange("note")

        vm.sort()
        assertEquals(DumpPhase.SORTING, vm.uiState.value.phase)
        vm.sort()

        coVerify(exactly = 1) { ai.brainDump(any(), any()) }
        gate.complete(Result.success(listOf(crash)))
        assertEquals(DumpPhase.RESULTS, vm.uiState.value.phase)
    }

    @Test
    fun `a note with no tasks goes back to the note with a hint`() {
        coEvery { ai.brainDump(any(), any()) } returns Result.success(emptyList())
        val vm = viewModel()
        vm.dump("just thinking out loud")

        assertEquals(DumpPhase.INPUT, vm.uiState.value.phase)
        assertEquals("just thinking out loud", vm.uiState.value.text)
        assertTrue(vm.uiState.value.message!!.contains("couldn't find any tasks"))
    }

    @Test
    fun `a failure keeps the note and shows the reason`() {
        coEvery { ai.brainDump(any(), any()) } returns Result.failure(AiException("The AI service is busy or over its limit. Try again in a moment."))
        val vm = viewModel()
        vm.dump("my note")

        val state = vm.uiState.value
        assertEquals(DumpPhase.INPUT, state.phase)
        assertEquals("my note", state.text)
        assertEquals("The AI service is busy or over its limit. Try again in a moment.", state.message)
        assertFalse(state.notConfigured)

        coEvery { ai.brainDump(any(), any()) } returns Result.success(listOf(crash))
        vm.sort()
        assertEquals(DumpPhase.RESULTS, vm.uiState.value.phase)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `a server without a key is its own state, and typing clears it`() {
        coEvery { ai.brainDump(any(), any()) } returns Result.failure(AiException("AI is not set up.", notConfigured = true))
        val vm = viewModel()
        vm.dump("x")
        assertTrue(vm.uiState.value.notConfigured)
        vm.onTextChange("xy")
        assertFalse(vm.uiState.value.notConfigured)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `a failing project lookup does not stop the sort`() {
        coEvery { projects.projects() } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel()
        vm.dump()
        assertEquals(DumpPhase.RESULTS, vm.uiState.value.phase)
        assertNull(vm.uiState.value.rows[0].projectColorHex)
        assertEquals("Design", vm.uiState.value.rows[0].projectName)
    }

    // ---- choosing and adding

    @Test
    fun `unticking drops a task and the count follows`() {
        val vm = viewModel()
        vm.dump()
        vm.toggle(1)
        assertEquals(2, vm.uiState.value.selectedCount)
        vm.toggle(1)
        assertEquals(3, vm.uiState.value.selectedCount)
        vm.toggle(9)
        assertEquals(3, vm.uiState.value.selectedCount)
    }

    @Test
    fun `adding creates only the ticked tasks with every detail, in order, then reports done`() = runTest {
        val created = mutableListOf<NewTask>()
        val slot = slot<NewTask>()
        coEvery { tasks.create(capture(slot)) } answers { created += slot.captured; Result.success(task()) }
        val vm = viewModel()
        vm.dump()
        vm.toggle(1)

        vm.added.test {
            vm.addSelected()
            awaitItem()
        }

        assertEquals(
            listOf(
                NewTask(title = "Email Priya the deck", projectId = "p-design", priority = Priority.NORMAL, dueAt = Instant.parse("2026-11-13T04:30:00Z")),
                NewTask(title = "Renew the gym", priority = Priority.NORMAL, repeatRule = "Every month"),
            ),
            created,
        )
    }

    @Test
    fun `with nothing ticked adding does nothing`() {
        val vm = viewModel()
        vm.dump()
        vm.toggle(0); vm.toggle(1); vm.toggle(2)
        vm.addSelected()
        coVerify(exactly = 0) { tasks.create(any()) }
    }

    @Test
    fun `a failure part way keeps only the tasks still to add`() = runTest {
        coEvery { tasks.create(match { it.title == "Fix the rotation crash" }) } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel()
        vm.dump()

        vm.addSelected()

        val state = vm.uiState.value
        assertFalse(state.adding)
        assertEquals(listOf("Fix the rotation crash", "Renew the gym"), state.rows.map { it.title })
        assertEquals("Added 1 of 3. Couldn't add the rest.", state.message)

        // Trying again must not create the first task twice.
        coEvery { tasks.create(match { it.title == "Fix the rotation crash" }) } returns Result.success(task())
        vm.added.test {
            vm.addSelected()
            awaitItem()
        }
        coVerify(exactly = 1) { tasks.create(match { it.title == "Email Priya the deck" }) }
        coVerifyOrder {
            tasks.create(match { it.title == "Fix the rotation crash" })
            tasks.create(match { it.title == "Renew the gym" })
        }
    }

    // ---- editing and resetting

    @Test
    fun `edit note goes back to the words and drops the results`() {
        val vm = viewModel()
        vm.dump("my note")
        vm.editNote()
        assertEquals(DumpPhase.INPUT, vm.uiState.value.phase)
        assertEquals("my note", vm.uiState.value.text)
        assertTrue(vm.uiState.value.rows.isEmpty())
    }

    @Test
    fun `start over clears everything`() {
        val vm = viewModel()
        vm.dump("my note")
        vm.startOver()
        assertEquals("", vm.uiState.value.text)
        assertEquals(DumpPhase.INPUT, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.rows.isEmpty())
    }
}
