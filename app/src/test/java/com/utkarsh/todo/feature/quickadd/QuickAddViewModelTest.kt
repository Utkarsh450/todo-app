package com.utkarsh.todo.feature.quickadd

import app.cash.turbine.test
import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import com.utkarsh.todo.feature.calendar.domain.NewEvent
import com.utkarsh.todo.feature.notes.domain.NotesRepository
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.quickadd.domain.QuickAddParser
import com.utkarsh.todo.feature.quickadd.domain.QuickAddType
import com.utkarsh.todo.feature.quickadd.domain.TokenKind
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
    private val calendar = mockk<CalendarRepository>()
    private val notes = mockk<NotesRepository>()

    private fun created(n: NewTask) = Task("t1", n.title, "", n.projectId, n.priority, TaskStatus.TODO, n.dueAt, null, false, false, n.repeatRule, emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { projects.projects() } returns Result.success(listOf(Project("p-design", "Design", "#935EDF")))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = QuickAddViewModel(tasks, projects, calendar, notes, QuickAddParser(clock), clock)

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

    // ---- notes ----

    @Test
    fun `a note is added exactly as typed to today's note and nothing is parsed out of it`() = runTest {
        coEvery { notes.append(any(), any()) } returns Result.success(Unit)
        val vm = viewModel()
        vm.onTypeChange(QuickAddType.NOTE)
        vm.onTextChange("Call mom tomorrow #family !high every monday")

        assertEquals(emptyList<Any>(), vm.uiState.value.parsed?.tokens)
        vm.submit()

        coVerify(exactly = 1) { notes.append(LocalDate.of(2026, 11, 7), "Call mom tomorrow #family !high every monday") }
        coVerify(exactly = 0) { tasks.create(any()) }
        coVerify(exactly = 0) { calendar.createEvent(any()) }
    }

    @Test
    fun `a note keeps its line breaks and only loses the outer whitespace`() = runTest {
        coEvery { notes.append(any(), any()) } returns Result.success(Unit)
        val vm = viewModel()
        vm.onTypeChange(QuickAddType.NOTE)
        vm.onTextChange("  first line\n\nsecond   line  ")

        vm.submit()

        coVerify { notes.append(any(), "first line\n\nsecond   line") }
    }

    @Test
    fun `a failed note keeps the text and names the note in the error`() = runTest {
        coEvery { notes.append(any(), any()) } returns Result.failure(RuntimeException())
        val vm = viewModel()
        vm.onTypeChange(QuickAddType.NOTE)
        vm.onTextChange("remember this")

        vm.submit()

        assertEquals("Couldn't add note", vm.uiState.value.error)
        assertEquals("remember this", vm.uiState.value.text)
        assertEquals(QuickAddType.NOTE, vm.uiState.value.type)
    }

    // ---- events ----

    private fun captureEvent(): io.mockk.CapturingSlot<NewEvent> {
        val slot = slot<NewEvent>()
        coEvery { calendar.createEvent(capture(slot)) } returns Result.success(Unit)
        return slot
    }

    @Test
    fun `an event with a day and a time lasts one hour and resolves its project`() = runTest {
        val event = captureEvent()
        val vm = viewModel()
        vm.onTypeChange(QuickAddType.EVENT)
        vm.onTextChange("Lunch with Sam fri 1pm #design")

        vm.submit()

        assertEquals("Lunch with Sam", event.captured.title)
        assertEquals(Instant.parse("2026-11-13T07:30:00Z"), event.captured.start) // Fri 13 Nov 13:00 IST
        assertEquals(Instant.parse("2026-11-13T08:30:00Z"), event.captured.end)
        assertFalse(event.captured.allDay)
        assertEquals("p-design", event.captured.projectId)
        coVerify(exactly = 0) { tasks.create(any()) }
    }

    @Test
    fun `an event given only a day is an all-day event`() = runTest {
        val event = captureEvent()
        val vm = viewModel()
        vm.onTypeChange(QuickAddType.EVENT)
        vm.onTextChange("Team offsite tomorrow")

        assertTrue(vm.uiState.value.isAllDayEvent)
        vm.submit()

        assertTrue(event.captured.allDay)
        assertEquals(Instant.parse("2026-11-07T18:30:00Z"), event.captured.start) // Sun 8 Nov 00:00 IST
        assertEquals(Instant.parse("2026-11-08T18:29:00Z"), event.captured.end) // Sun 8 Nov 23:59 IST
    }

    @Test
    fun `an event with no date is one hour from the next full hour`() = runTest {
        val event = captureEvent()
        val vm = viewModel()
        vm.onTypeChange(QuickAddType.EVENT)
        vm.onTextChange("Standup")

        assertFalse(vm.uiState.value.isAllDayEvent)
        vm.submit()

        assertEquals(Instant.parse("2026-11-07T05:30:00Z"), event.captured.start) // 11:00 IST
        assertEquals(Instant.parse("2026-11-07T06:30:00Z"), event.captured.end)
        assertFalse(event.captured.allDay)
    }

    @Test
    fun `an event keeps words that only mean something to a task`() = runTest {
        val event = captureEvent()
        val vm = viewModel()
        vm.onTypeChange(QuickAddType.EVENT)
        vm.onTextChange("Team sync !high every week friday 2pm")

        assertEquals(listOf(TokenKind.DAY), vm.uiState.value.parsed?.tokens?.map { it.kind })
        vm.submit()

        assertEquals("Team sync !high every week", event.captured.title)
        assertEquals(Instant.parse("2026-11-13T08:30:00Z"), event.captured.start) // Fri 14:00 IST
    }

    @Test
    fun `a failed event keeps the text and names the event in the error`() = runTest {
        coEvery { calendar.createEvent(any()) } returns Result.failure(RuntimeException())
        val vm = viewModel()
        vm.onTypeChange(QuickAddType.EVENT)
        vm.onTextChange("Dentist tomorrow 9am")

        vm.submit()

        assertEquals("Couldn't add event", vm.uiState.value.error)
        assertEquals("Dentist tomorrow 9am", vm.uiState.value.text)
        assertFalse(vm.uiState.value.isSaving)
    }

    // ---- switching ----

    @Test
    fun `switching type keeps the text and reads it again by the new rules`() {
        val vm = viewModel()
        vm.onTextChange("Plan trip tomorrow #Design !high")
        assertEquals(setOf(TokenKind.DAY, TokenKind.PROJECT, TokenKind.PRIORITY), vm.uiState.value.parsed?.tokens?.map { it.kind }?.toSet())

        vm.onTypeChange(QuickAddType.EVENT)
        assertEquals(setOf(TokenKind.DAY, TokenKind.PROJECT), vm.uiState.value.parsed?.tokens?.map { it.kind }?.toSet())
        assertEquals("Plan trip !high", vm.uiState.value.parsed?.title)

        vm.onTypeChange(QuickAddType.NOTE)
        assertEquals(emptyList<Any>(), vm.uiState.value.parsed?.tokens)
        assertEquals("Plan trip tomorrow #Design !high", vm.uiState.value.parsed?.title)

        vm.onTypeChange(QuickAddType.TASK)
        assertEquals(3, vm.uiState.value.parsed?.tokens?.size)
        assertEquals("Plan trip tomorrow #Design !high", vm.uiState.value.text)
    }

    @Test
    fun `switching type clears an old error`() = runTest {
        coEvery { tasks.create(any()) } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel()
        vm.onTextChange("Buy oat milk")
        vm.submit()
        assertEquals("offline", vm.uiState.value.error)

        vm.onTypeChange(QuickAddType.NOTE)

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `after adding a note the sheet is back to an empty task`() = runTest {
        coEvery { notes.append(any(), any()) } returns Result.success(Unit)
        val vm = viewModel()
        vm.onTypeChange(QuickAddType.NOTE)
        vm.onTextChange("jot")

        vm.added.test {
            vm.submit()
            awaitItem()
        }

        assertEquals(QuickAddType.TASK, vm.uiState.value.type)
        assertEquals("", vm.uiState.value.text)
    }

    @Test
    fun `every type needs some text before it can be added`() {
        val vm = viewModel()
        QuickAddType.entries.forEach { type ->
            vm.onTypeChange(type)
            vm.onTextChange("")
            assertFalse("$type with nothing typed", vm.uiState.value.canSubmit)
            vm.onTextChange("something")
            assertTrue("$type with text", vm.uiState.value.canSubmit)
        }
    }
}
