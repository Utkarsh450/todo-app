package com.utkarsh.todo.feature.calendar

import com.utkarsh.todo.feature.calendar.domain.AgendaItem
import com.utkarsh.todo.feature.calendar.domain.AgendaKind
import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import com.utkarsh.todo.feature.calendar.ui.CalendarMode
import com.utkarsh.todo.feature.calendar.ui.CalendarViewModel
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
class CalendarViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), zone) // Sat 7 Nov 10:00 IST
    private val calendar = mockk<CalendarRepository>()
    private val projects = mockk<ProjectsRepository>()
    private val tasks = mockk<TasksRepository>()
    private val changes = MutableSharedFlow<Unit>()
    private val eventChanges = MutableSharedFlow<Unit>()

    private fun item(id: String, title: String, startIst: String, endIst: String? = null, project: String? = "p-eng", kind: AgendaKind = AgendaKind.EVENT, allDay: Boolean = false, done: Boolean = false) =
        AgendaItem(kind, id, title, ist(startIst), endIst?.let(::ist), allDay, project, done)

    private fun ist(local: String): Instant = java.time.LocalDateTime.parse(local).atZone(zone).toInstant()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { tasks.changes } returns changes
        every { calendar.changes } returns eventChanges
        coEvery { projects.projects() } returns Result.success(listOf(Project("p-eng", "Engineering", "#2784D5"), Project("p-des", "Design", "#935EDF")))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(items: List<AgendaItem> = emptyList()): CalendarViewModel {
        coEvery { calendar.agenda(any(), any()) } returns Result.success(items)
        return CalendarViewModel(calendar, projects, tasks, clock)
    }

    @Test
    fun `starts on the current month with today selected and the right grid range requested`() {
        val from = slot<Instant>()
        val to = slot<Instant>()
        coEvery { calendar.agenda(capture(from), capture(to)) } returns Result.success(emptyList())

        val state = CalendarViewModel(calendar, projects, tasks, clock).uiState.value

        assertEquals("November", state.monthLabel)
        assertEquals("2026", state.yearLabel)
        assertEquals(35, state.cells.size)
        assertEquals(LocalDate.of(2026, 11, 7), state.cells.single { it.isSelected }.date)
        assertTrue(state.cells.single { it.isToday }.date == LocalDate.of(2026, 11, 7))
        assertEquals(ist("2026-11-01T00:00"), from.captured)
        assertEquals(ist("2026-12-06T00:00"), to.captured) // grid ends Sat 5 Dec
        assertEquals("Sat, 7 November", state.selectedLabel)
    }

    @Test
    fun `cells show distinct project colour dots capped at three, and mark out of month days`() {
        val vm = viewModel(
            listOf(
                item("a", "A", "2026-11-07T09:00", project = "p-eng"),
                item("b", "B", "2026-11-07T10:00", project = "p-eng"),
                item("c", "C", "2026-11-07T11:00", project = "p-des"),
                item("d", "D", "2026-11-07T12:00", project = null),
                item("e", "E", "2026-11-07T13:00", project = "p-des"),
            ),
        )

        val seventh = vm.uiState.value.cells.single { it.date == LocalDate.of(2026, 11, 7) }
        assertEquals(listOf("#2784D5", "#935EDF", null), seventh.dots)
        assertTrue(vm.uiState.value.cells.last().date.month.value == 12 && !vm.uiState.value.cells.last().inMonth)
    }

    @Test
    fun `agenda lists the selected day sorted with times and project info`() {
        val vm = viewModel(
            listOf(
                item("late", "Groceries", "2026-11-07T17:30", kind = AgendaKind.TASK, project = null),
                item("early", "Review PR", "2026-11-07T09:00", "2026-11-07T10:30"),
                item("other", "Tomorrow", "2026-11-08T09:00"),
            ),
        )

        val state = vm.uiState.value
        assertEquals(listOf("early", "late"), state.agenda.map { it.id })
        assertEquals("9:00 AM", state.agenda.first().timeLabel)
        assertEquals("Engineering", state.agenda.first().projectName)
        assertEquals("2 planned", state.plannedLabel)
    }

    @Test
    fun `selecting another day swaps the agenda`() {
        val vm = viewModel(listOf(item("a", "A", "2026-11-07T09:00"), item("b", "B", "2026-11-09T09:00")))

        vm.select(LocalDate.of(2026, 11, 9))

        assertEquals(listOf("b"), vm.uiState.value.agenda.map { it.id })
        assertEquals("Mon, 9 November", vm.uiState.value.selectedLabel)
    }

    @Test
    fun `empty day has no planned label`() {
        assertEquals("", viewModel().uiState.value.plannedLabel)
    }

    @Test
    fun `next and previous month reload the grid and select the first day`() {
        val vm = viewModel()

        vm.nextMonth()
        assertEquals("December", vm.uiState.value.monthLabel)
        assertEquals(LocalDate.of(2026, 12, 1), vm.uiState.value.cells.single { it.isSelected }.date)

        vm.previousMonth()
        vm.previousMonth()
        assertEquals("October", vm.uiState.value.monthLabel)
        assertEquals(LocalDate.of(2026, 10, 1), vm.uiState.value.cells.single { it.isSelected }.date)
    }

    @Test
    fun `returning to this month reselects today`() {
        val vm = viewModel()
        vm.nextMonth()

        vm.goToToday()

        assertEquals("November", vm.uiState.value.monthLabel)
        assertEquals(LocalDate.of(2026, 11, 7), vm.uiState.value.cells.single { it.isSelected }.date)
    }

    @Test
    fun `tapping a padding day from the next month switches to that month`() {
        val vm = viewModel()

        vm.select(LocalDate.of(2026, 12, 3)) // visible as trailing padding

        assertEquals("December", vm.uiState.value.monthLabel)
        assertEquals(LocalDate.of(2026, 12, 3), vm.uiState.value.cells.single { it.isSelected }.date)
    }

    @Test
    fun `week mode builds the week strip and a timeline with lanes for overlaps`() {
        val vm = viewModel(
            listOf(
                item("a", "Review PR", "2026-11-07T09:00", "2026-11-07T10:30"),
                item("b", "Design crit", "2026-11-07T10:00", "2026-11-07T11:00", project = "p-des"),
                item("c", "Call", "2026-11-07T15:00", kind = AgendaKind.TASK, project = null),
                item("allday", "Release", "2026-11-07T00:00", "2026-11-07T23:59", allDay = true),
            ),
        )

        vm.setMode(CalendarMode.WEEK)

        val state = vm.uiState.value
        assertEquals(CalendarMode.WEEK, state.mode)
        assertEquals(7, state.weekDays.size)
        assertEquals(LocalDate.of(2026, 11, 1), state.weekDays.first().date)
        assertTrue(state.weekDays.single { it.isSelected }.hasItems)
        assertFalse(state.weekDays.first().hasItems)

        val timeline = state.timeline
        assertEquals(9, timeline.startHour)
        assertEquals(18, timeline.endHour)
        assertEquals(listOf("allday"), timeline.allDay.map { it.id })
        val a = timeline.blocks.single { it.id == "a" }
        val b = timeline.blocks.single { it.id == "b" }
        assertEquals(0, a.startMinute)
        assertEquals(90, a.durationMinutes)
        assertEquals(setOf(0, 1), setOf(a.lane, b.lane))
        assertEquals(2, a.laneCount)
        val task = timeline.blocks.single { it.id == "c" }
        assertEquals(360, task.startMinute)
        assertEquals(30, task.durationMinutes) // tasks get the minimum block
        assertEquals(1, task.laneCount)
    }

    @Test
    fun `timeline range grows to fit early and late items`() {
        val vm = viewModel(
            listOf(
                item("early", "Flight", "2026-11-07T06:30", "2026-11-07T08:00"),
                item("late", "Dinner", "2026-11-07T20:00", "2026-11-07T21:15"),
            ),
        )

        vm.setMode(CalendarMode.WEEK)

        assertEquals(6, vm.uiState.value.timeline.startHour)
        assertEquals(22, vm.uiState.value.timeline.endHour)
        assertEquals(30, vm.uiState.value.timeline.blocks.single { it.id == "early" }.startMinute)
    }

    @Test
    fun `load failure shows an error and refresh recovers`() = runTest {
        coEvery { calendar.agenda(any(), any()) } returns Result.failure(RuntimeException("offline"))
        val vm = CalendarViewModel(calendar, projects, tasks, clock)
        assertEquals("offline", vm.uiState.value.error)
        assertTrue(vm.uiState.value.cells.isEmpty())

        coEvery { calendar.agenda(any(), any()) } returns Result.success(emptyList())
        vm.refresh()

        assertNull(vm.uiState.value.error)
        assertEquals(35, vm.uiState.value.cells.size)
    }

    @Test
    fun `reloads when tasks change elsewhere`() = runTest {
        viewModel()

        changes.emit(Unit)

        coVerify(exactly = 2) { calendar.agenda(any(), any()) }
    }

    @Test
    fun `a newly created event shows up without leaving the screen`() = runTest {
        val vm = viewModel()
        coVerify(exactly = 1) { calendar.agenda(any(), any()) }

        eventChanges.emit(Unit)

        coVerify(exactly = 2) { calendar.agenda(any(), any()) }
        assertEquals(false, vm.uiState.value.isLoading)
    }
}
