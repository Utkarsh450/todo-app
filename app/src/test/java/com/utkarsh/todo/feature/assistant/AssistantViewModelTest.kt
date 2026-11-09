package com.utkarsh.todo.feature.assistant

import com.utkarsh.todo.feature.ai.domain.AiException
import com.utkarsh.todo.feature.ai.domain.AiRepository
import com.utkarsh.todo.feature.ai.domain.AssistantReply
import com.utkarsh.todo.feature.ai.domain.AssistantSuggestion
import com.utkarsh.todo.feature.assistant.ui.AssistantViewModel
import com.utkarsh.todo.feature.assistant.ui.SuggestionKind
import com.utkarsh.todo.feature.assistant.ui.SuggestionStatus
import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import com.utkarsh.todo.feature.calendar.domain.NewEvent
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.tasks.domain.Field
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Subtask
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-08T04:30:00Z"), zone) // Sun 8 Nov, 10:00 IST
    private val ai = mockk<AiRepository>()
    private val tasks = mockk<TasksRepository>()
    private val calendar = mockk<CalendarRepository>()
    private val reminders = mockk<RemindersRepository>()

    private val move = AssistantSuggestion.Move("a", "Review PR #482", "Your sharpest hour", Instant.parse("2026-11-09T03:30:00Z")) // Mon 9:00 IST
    private val split = AssistantSuggestion.Split("b", "Ship onboarding redesign", "Too big", listOf("Outline", "Draft", "Review"))
    private val priority = AssistantSuggestion.ChangePriority("c", "Pay bill", "Due soon", Priority.URGENT)

    private fun task(id: String) = Task(id, "t", "", null, Priority.NORMAL, TaskStatus.TODO, null, null, false, false, null, emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { ai.assist(any(), any()) } returns Result.success(AssistantReply("A lighter shape for today.", listOf(move, split, priority)))
        coEvery { tasks.update(any(), any()) } answers { Result.success(task(firstArg())) }
        coEvery { tasks.create(any()) } returns Result.success(task("new"))
        coEvery { tasks.setDone(any(), any()) } answers { Result.success(task(firstArg())) }
        coEvery { calendar.createEvent(any()) } returns Result.success("e1")
        coEvery { reminders.create(any()) } answers {
            val r = firstArg<NewReminder>()
            Result.success(Reminder("r1", null, r.title, r.remindAt, r.repeatRule, null, true, null))
        }
        coEvery { tasks.addSubtask(any(), any()) } answers { Result.success(Subtask("s", firstArg(), secondArg(), false)) }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AssistantViewModel(ai, tasks, calendar, reminders, clock)

    private fun AssistantViewModel.statuses() = uiState.value.turns.last().suggestions.map { it.status }

    // ---- asking

    @Test
    fun `asking shows the question, the reply and readable suggestions`() {
        val vm = viewModel()
        vm.onInputChange("  Plan my day  ")

        vm.ask()

        val state = vm.uiState.value
        assertEquals("", state.input)
        val turn = state.turns.single()
        assertEquals("Plan my day", turn.question)
        assertEquals("A lighter shape for today.", turn.reply)
        assertFalse(turn.loading)
        assertEquals(listOf(SuggestionKind.MOVE, SuggestionKind.SPLIT, SuggestionKind.PRIORITY), turn.suggestions.map { it.kind })
        assertEquals(listOf("Review PR #482 → Tomorrow, 9:00 AM", "Ship onboarding redesign", "Pay bill → Urgent"), turn.suggestions.map { it.title })
        assertEquals(listOf("Outline", "Draft", "Review"), turn.suggestions[1].steps)
        assertTrue(turn.suggestions.all { it.status == SuggestionStatus.PENDING })
        coVerify(exactly = 1) { ai.assist("Plan my day", zone) }
    }

    @Test
    fun `a blank question is ignored`() {
        val vm = viewModel()
        vm.onInputChange("   ")
        vm.ask()
        vm.ask("")
        assertTrue(vm.uiState.value.turns.isEmpty())
        coVerify(exactly = 0) { ai.assist(any(), any()) }
    }

    @Test
    fun `a quick prompt asks it as typed and leaves the draft alone`() {
        val vm = viewModel()
        vm.onInputChange("half a thought")

        vm.ask("What's slipping?")

        assertEquals("What's slipping?", vm.uiState.value.turns.single().question)
        assertEquals("half a thought", vm.uiState.value.input)
    }

    @Test
    fun `nothing is asked while an answer is pending`() {
        val gate = CompletableDeferred<Result<AssistantReply>>()
        coEvery { ai.assist(any(), any()) } coAnswers { gate.await() }
        val vm = viewModel()

        vm.ask("first")
        assertTrue(vm.uiState.value.busy)
        vm.ask("second")

        assertEquals(1, vm.uiState.value.turns.size)
        gate.complete(Result.success(AssistantReply("done", emptyList())))
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `a reply with no suggestions is just words`() {
        coEvery { ai.assist(any(), any()) } returns Result.success(AssistantReply("Nothing is late.", emptyList()))
        val vm = viewModel()
        vm.ask("What's slipping?")
        val turn = vm.uiState.value.turns.single()
        assertEquals("Nothing is late.", turn.reply)
        assertTrue(turn.suggestions.isEmpty())
    }

    @Test
    fun `a failure shows its message and retry asks the same question again`() {
        coEvery { ai.assist(any(), any()) } returns Result.failure(AiException("The AI service is busy or over its limit. Try again in a moment."))
        val vm = viewModel()
        vm.ask("Plan my day")

        val failed = vm.uiState.value.turns.single()
        assertEquals("The AI service is busy or over its limit. Try again in a moment.", failed.error)
        assertFalse(failed.notConfigured)

        coEvery { ai.assist(any(), any()) } returns Result.success(AssistantReply("Here you go", emptyList()))
        vm.retry(failed.id)

        val turn = vm.uiState.value.turns.single()
        assertNull(turn.error)
        assertEquals("Here you go", turn.reply)
        coVerify(exactly = 2) { ai.assist("Plan my day", zone) }
    }

    @Test
    fun `a server without a key is its own state`() {
        coEvery { ai.assist(any(), any()) } returns Result.failure(AiException("AI is not set up.", notConfigured = true))
        val vm = viewModel()
        vm.ask("hi")
        assertTrue(vm.uiState.value.turns.single().notConfigured)
    }

    // ---- accepting

    @Test
    fun `accepting a move changes only the due time`() {
        val vm = viewModel()
        vm.ask("Plan my day")

        vm.accept(vm.uiState.value.turns.single().suggestions[0].id)

        coVerify(exactly = 1) { tasks.update("a", TaskPatch(dueAt = Field.Set(move.dueAt))) }
        assertEquals(listOf(SuggestionStatus.ACCEPTED, SuggestionStatus.PENDING, SuggestionStatus.PENDING), vm.statuses())
    }

    @Test
    fun `accepting a priority change sets only the priority`() {
        val vm = viewModel()
        vm.ask("x")

        vm.accept(vm.uiState.value.turns.single().suggestions[2].id)

        coVerify(exactly = 1) { tasks.update("c", TaskPatch(priority = Field.Set(Priority.URGENT))) }
    }

    @Test
    fun `accepting a split adds each step under the task in order`() {
        val vm = viewModel()
        vm.ask("x")

        vm.accept(vm.uiState.value.turns.single().suggestions[1].id)

        coVerifyOrder {
            tasks.addSubtask("b", "Outline")
            tasks.addSubtask("b", "Draft")
            tasks.addSubtask("b", "Review")
        }
        assertEquals(SuggestionStatus.ACCEPTED, vm.statuses()[1])
    }

    @Test
    fun `nothing is applied until the person accepts`() {
        viewModel().ask("x")
        coVerify(exactly = 0) { tasks.update(any(), any()) }
        coVerify(exactly = 0) { tasks.addSubtask(any(), any()) }
    }

    @Test
    fun `skipping applies nothing and a skipped suggestion cannot then be accepted`() {
        val vm = viewModel()
        vm.ask("x")
        val id = vm.uiState.value.turns.single().suggestions[0].id

        vm.skip(id)
        vm.accept(id)

        assertEquals(SuggestionStatus.SKIPPED, vm.statuses()[0])
        coVerify(exactly = 0) { tasks.update(any(), any()) }
    }

    @Test
    fun `an accepted suggestion is applied once even if accepted again`() {
        val vm = viewModel()
        vm.ask("x")
        val id = vm.uiState.value.turns.single().suggestions[0].id

        vm.accept(id)
        vm.accept(id)

        coVerify(exactly = 1) { tasks.update("a", any()) }
    }

    @Test
    fun `accept all applies every pending suggestion but skips the skipped ones`() {
        val vm = viewModel()
        vm.ask("x")
        val turn = vm.uiState.value.turns.single()
        vm.skip(turn.suggestions[2].id)
        assertEquals(2, vm.uiState.value.turns.single().pendingCount)

        vm.acceptAll(turn.id)

        assertEquals(listOf(SuggestionStatus.ACCEPTED, SuggestionStatus.ACCEPTED, SuggestionStatus.SKIPPED), vm.statuses())
        coVerify(exactly = 1) { tasks.update("a", any()) }
        coVerify(exactly = 3) { tasks.addSubtask("b", any()) }
        coVerify(exactly = 0) { tasks.update("c", any()) }
    }

    @Test
    fun `a failed change can be accepted again`() {
        coEvery { tasks.update("a", any()) } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel()
        vm.ask("x")
        val id = vm.uiState.value.turns.single().suggestions[0].id

        vm.accept(id)
        assertEquals(SuggestionStatus.FAILED, vm.statuses()[0])

        coEvery { tasks.update("a", any()) } returns Result.success(task("a"))
        vm.accept(id)
        assertEquals(SuggestionStatus.ACCEPTED, vm.statuses()[0])
    }

    @Test
    fun `a split that fails part way keeps only the steps still to add`() {
        coEvery { tasks.addSubtask("b", "Draft") } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel()
        vm.ask("x")
        val id = vm.uiState.value.turns.single().suggestions[1].id

        vm.accept(id)

        val failed = vm.uiState.value.turns.single().suggestions[1]
        assertEquals(SuggestionStatus.FAILED, failed.status)
        assertEquals(listOf("Draft", "Review"), failed.steps)

        coEvery { tasks.addSubtask("b", "Draft") } answers { Result.success(Subtask("s", "b", "Draft", false)) }
        vm.accept(id)

        assertEquals(SuggestionStatus.ACCEPTED, vm.statuses()[1])
        coVerify(exactly = 1) { tasks.addSubtask("b", "Outline") } // never duplicated
    }

    @Test
    fun `earlier answers stay and suggestions from different turns do not clash`() {
        val vm = viewModel()
        vm.ask("first")
        vm.ask("second")

        val ids = vm.uiState.value.turns.flatMap { it.suggestions }.map { it.id }
        assertEquals(2, vm.uiState.value.turns.size)
        assertEquals(ids.size, ids.toSet().size)
    }

    // ---- creating things

    private val newTask = AssistantSuggestion.CreateTask("Buy milk", "Low at home", Instant.parse("2026-11-09T03:30:00Z"), Priority.HIGH, "Every week", "p1", "Home")
    private val newEvent = AssistantSuggestion.CreateEvent("Dentist", "", Instant.parse("2026-11-10T09:30:00Z"), Instant.parse("2026-11-10T10:30:00Z"), false)
    private val newReminder = AssistantSuggestion.CreateReminder("Stretch", "", Instant.parse("2026-11-08T08:30:00Z"), "Every day")

    @Test
    fun `new things are described and labelled by kind`() {
        coEvery { ai.assist(any(), any()) } returns Result.success(AssistantReply("Added.", listOf(newTask, newEvent, newReminder, AssistantSuggestion.Complete("a", "Pay bill", ""))))
        val vm = viewModel()
        vm.ask("add things")

        val ui = vm.uiState.value.turns.single().suggestions
        assertEquals(listOf(SuggestionKind.TASK, SuggestionKind.EVENT, SuggestionKind.REMINDER, SuggestionKind.COMPLETE), ui.map { it.kind })
        assertTrue(ui[0].title.startsWith("Buy milk — "))
        assertTrue(ui[0].title.contains("Every week") && ui[0].title.contains("#Home") && ui[0].title.contains("high"))
        assertTrue(ui[1].title.startsWith("Dentist — "))
        assertTrue(ui[2].title.endsWith("Every day"))
        assertEquals("Pay bill", ui[3].title)
        coVerify(exactly = 0) { tasks.create(any()) }
        coVerify(exactly = 0) { calendar.createEvent(any()) }
        coVerify(exactly = 0) { reminders.create(any()) }
    }

    @Test
    fun `accepting creates the task, the event and the reminder through the repositories`() {
        coEvery { ai.assist(any(), any()) } returns Result.success(AssistantReply("Added.", listOf(newTask, newEvent, newReminder, AssistantSuggestion.Complete("a", "Pay bill", ""))))
        val vm = viewModel()
        vm.ask("add things")

        vm.acceptAll(vm.uiState.value.turns.single().id)

        assertEquals(List(4) { SuggestionStatus.ACCEPTED }, vm.statuses())
        coVerify(exactly = 1) { tasks.create(NewTask(title = "Buy milk", projectId = "p1", priority = Priority.HIGH, dueAt = Instant.parse("2026-11-09T03:30:00Z"), repeatRule = "Every week")) }
        coVerify(exactly = 1) { calendar.createEvent(NewEvent("Dentist", Instant.parse("2026-11-10T09:30:00Z"), Instant.parse("2026-11-10T10:30:00Z"), false)) }
        coVerify(exactly = 1) { reminders.create(NewReminder(title = "Stretch", remindAt = Instant.parse("2026-11-08T08:30:00Z"), repeatRule = "Every day")) }
        coVerify(exactly = 1) { tasks.setDone("a", true) }
    }

    @Test
    fun `a creation that fails can be retried and is only made once`() {
        coEvery { ai.assist(any(), any()) } returns Result.success(AssistantReply("ok", listOf(newEvent)))
        coEvery { calendar.createEvent(any()) } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel()
        vm.ask("x")
        val id = vm.uiState.value.turns.single().suggestions.single().id

        vm.accept(id)
        assertEquals(SuggestionStatus.FAILED, vm.statuses().single())

        coEvery { calendar.createEvent(any()) } returns Result.success("e1")
        vm.accept(id)
        assertEquals(SuggestionStatus.ACCEPTED, vm.statuses().single())
        vm.accept(id) // already done: ignored
        coVerify(exactly = 2) { calendar.createEvent(any()) }
    }
}
