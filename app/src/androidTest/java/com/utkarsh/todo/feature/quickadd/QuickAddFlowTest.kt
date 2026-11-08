package com.utkarsh.todo.feature.quickadd

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.calendar.domain.AgendaItem
import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import com.utkarsh.todo.feature.calendar.domain.NewEvent
import com.utkarsh.todo.feature.notes.domain.NotesRepository
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.quickadd.domain.QuickAddParser
import com.utkarsh.todo.feature.quickadd.ui.QuickAddContent
import com.utkarsh.todo.feature.quickadd.ui.QuickAddViewModel
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Subtask
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/** The real QuickAddViewModel and the real sheet content, wired together over in-memory repositories. */
class QuickAddFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), ZoneId.of("Asia/Kolkata"))

    private val createdTasks = mutableListOf<NewTask>()
    private val createdEvents = mutableListOf<NewEvent>()
    private val appendedNotes = mutableListOf<Pair<LocalDate, String>>()
    private var eventFails = false

    private val tasks = object : TasksRepository {
        override val changes: Flow<Unit> = MutableSharedFlow()
        override suspend fun create(task: NewTask): Result<Task> { createdTasks += task; return Result.failure(IllegalStateException("not needed")) }
        override suspend fun dueBetween(from: Instant, to: Instant) = Result.success(emptyList<Task>())
        override suspend fun search(query: String) = Result.success(emptyList<Task>())
        override suspend fun get(id: String): Result<Task> = error("unused")
        override suspend fun update(id: String, patch: TaskPatch): Result<Task> = error("unused")
        override suspend fun setDone(id: String, done: Boolean): Result<Task> = error("unused")
        override suspend fun delete(id: String): Result<Unit> = error("unused")
        override suspend fun addSubtask(taskId: String, title: String): Result<Subtask> = error("unused")
        override suspend fun setSubtaskDone(id: String, done: Boolean): Result<Subtask> = error("unused")
    }
    private val projects = object : ProjectsRepository {
        override suspend fun projects() = Result.success(listOf(Project("p-design", "Design", "#935EDF")))
        override suspend fun create(name: String, colorHex: String): Result<Project> = error("unused")
    }
    private val calendar = object : CalendarRepository {
        override val changes: Flow<Unit> = MutableSharedFlow()
        override suspend fun agenda(from: Instant, to: Instant) = Result.success(emptyList<AgendaItem>())
        override suspend fun createEvent(event: NewEvent): Result<Unit> {
            createdEvents += event
            return if (eventFails) Result.failure(RuntimeException("offline")) else Result.success(Unit)
        }
    }
    private val notes = object : NotesRepository {
        override val changes: Flow<Unit> = MutableSharedFlow()
        override suspend fun note(day: LocalDate) = Result.success("")
        override suspend fun save(day: LocalDate, text: String) = Result.success(Unit)
        override suspend fun append(day: LocalDate, text: String): Result<Unit> { appendedNotes += day to text; return Result.success(Unit) }
    }

    private var closed = 0

    private fun show() {
        val vm = QuickAddViewModel(tasks, projects, calendar, notes, QuickAddParser(clock), clock)
        compose.setContent {
            PaperTheme {
                val state by vm.uiState.collectAsState()
                androidx.compose.runtime.LaunchedEffect(vm) { vm.added.collect { closed++ } }
                QuickAddContent(state, vm::onTextChange, vm::submit, onTypeChange = vm::onTypeChange)
            }
        }
    }

    private fun type(text: String) = compose.onNodeWithTag("quick-add-input").performTextInput(text)

    @Test
    fun adding_a_note_sends_the_raw_text_to_todays_scratchpad_and_closes() {
        show()
        compose.onNodeWithText("Note").performClick()
        type("Call mom tomorrow #family")
        compose.onNodeWithTag("quick-add-submit").performClick()

        compose.waitUntil(5_000) { appendedNotes.isNotEmpty() }
        assertEquals(listOf(LocalDate.of(2026, 11, 7) to "Call mom tomorrow #family"), appendedNotes)
        compose.waitUntil(5_000) { closed == 1 }
        assertEquals(emptyList<Any>(), createdTasks)
        assertEquals(emptyList<Any>(), createdEvents)
    }

    @Test
    fun adding_an_event_sends_a_one_hour_event_and_closes() {
        show()
        compose.onNodeWithText("Event").performClick()
        type("Lunch with Sam fri 1pm #design")
        compose.onNodeWithText("■ design").assertIsDisplayed()
        compose.onNodeWithTag("quick-add-submit").performClick()

        compose.waitUntil(5_000) { createdEvents.isNotEmpty() }
        val event = createdEvents.single()
        assertEquals("Lunch with Sam", event.title)
        assertEquals(Instant.parse("2026-11-13T07:30:00Z"), event.start)
        assertEquals(Instant.parse("2026-11-13T08:30:00Z"), event.end)
        assertEquals(false, event.allDay)
        assertEquals("p-design", event.projectId)
        compose.waitUntil(5_000) { closed == 1 }
    }

    @Test
    fun an_all_day_event_is_flagged_and_sent_as_all_day() {
        show()
        compose.onNodeWithText("Event").performClick()
        type("Offsite tomorrow")
        compose.onNodeWithText("◷ All day").assertIsDisplayed()
        compose.onNodeWithTag("quick-add-submit").performClick()

        compose.waitUntil(5_000) { createdEvents.isNotEmpty() }
        assertEquals(true, createdEvents.single().allDay)
    }

    @Test
    fun a_failed_event_shows_the_error_and_keeps_what_was_typed() {
        eventFails = true
        show()
        compose.onNodeWithText("Event").performClick()
        type("Dentist tomorrow 9am")
        compose.onNodeWithTag("quick-add-submit").performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount("offline") > 0 }
        compose.onNodeWithText("offline").assertIsDisplayed()
        compose.onNodeWithText("Add event").assertIsDisplayed() // still an event, still editable
        assertEquals(0, closed)
        assertNotNull(createdEvents.singleOrNull())
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes().size
}
