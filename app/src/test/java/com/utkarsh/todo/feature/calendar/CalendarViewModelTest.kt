package com.utkarsh.todo.feature.calendar

import com.utkarsh.todo.feature.calendar.domain.AgendaItem
import com.utkarsh.todo.feature.calendar.domain.AgendaKind
import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import com.utkarsh.todo.feature.calendar.domain.CreateKind
import com.utkarsh.todo.feature.calendar.domain.NewEvent
import com.utkarsh.todo.feature.calendar.ui.CreateStatus
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
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
import java.time.LocalTime
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
    private val reminders = mockk<RemindersRepository>()
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
        return CalendarViewModel(calendar, projects, tasks, reminders, clock)
    }

    @Test
    fun `starts on the current month with today selected and the right grid range requested`() {
        val from = slot<Instant>()
        val to = slot<Instant>()
        coEvery { calendar.agenda(capture(from), capture(to)) } returns Result.success(emptyList())

        val state = CalendarViewModel(calendar, projects, tasks, reminders, clock).uiState.value

        assertEquals("November", state.monthLabel)
        assertEquals("2026", state.yearLabel)
        assertEquals(35, state.cells.size)
        assertEquals(LocalDate.of(2026, 11, 7), state.cells.single { it.isSelected }.date)
        assertTrue(state.cells.single { it.isToday }.date == LocalDate.of(2026, 11, 7))
        assertEquals(ist("2026-11-01T00:00"), from.captured)
        assertEquals(ist("2026-12-06T00:00"), to.captured) // grid ends Sat 5 Dec
        assertEquals("Sat, 7 November", state.selectedLabel)
        assertEquals(CalendarMode.MONTH, state.mode)
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
    fun `returning to this month reselects today`() {
        val vm = viewModel()
        vm.next()

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
    fun `load failure shows an error and refresh recovers`() = runTest {
        coEvery { calendar.agenda(any(), any()) } returns Result.failure(RuntimeException("offline"))
        val vm = CalendarViewModel(calendar, projects, tasks, reminders, clock)
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

    // ---- month cells

    @Test
    fun `a month cell shows its first three entries as bars, all day first, and counts the rest`() {
        val vm = viewModel(
            listOf(
                item("a", "A", "2026-11-07T11:00"),
                item("b", "B", "2026-11-07T09:00", project = null),
                item("c", "C", "2026-11-07T12:00", kind = AgendaKind.TASK, project = null),
                item("d", "D", "2026-11-07T13:00", project = "p-des"),
                item("e", "E", "2026-11-07T00:00", "2026-11-07T23:59", allDay = true, project = null),
            ),
        )

        val cell = vm.uiState.value.cells.single { it.date == LocalDate.of(2026, 11, 7) }

        assertEquals(listOf("e", "b", "a"), cell.chips.map { it.id })
        assertEquals("#2784D5", cell.chips.last().colorHex)
        assertEquals(2, cell.more)
        assertTrue(vm.uiState.value.cells.last().date.month.value == 12 && !vm.uiState.value.cells.last().inMonth)
    }

    @Test
    fun `reminders appear on the calendar as reminders`() {
        val vm = viewModel(listOf(item("r", "Drink water", "2026-11-07T11:00", kind = AgendaKind.REMINDER, project = null)))

        assertEquals(AgendaKind.REMINDER, vm.uiState.value.agenda.single().kind)
        assertEquals(AgendaKind.REMINDER, vm.uiState.value.cells.single { it.isSelected }.chips.single().kind)
    }

    // ---- moving around, per view

    @Test
    fun `previous and next move a month, a week or a day depending on the view`() {
        val vm = viewModel()

        vm.next()
        assertEquals("December", vm.uiState.value.monthLabel)
        assertEquals(LocalDate.of(2026, 12, 7), vm.uiState.value.cells.single { it.isSelected }.date)
        vm.previous(); vm.previous()
        assertEquals("October", vm.uiState.value.monthLabel)

        vm.goToToday()
        vm.setMode(CalendarMode.WEEK)
        vm.next()
        assertEquals(LocalDate.of(2026, 11, 14), vm.uiState.value.columns.single { it.isSelected }.date)

        vm.setMode(CalendarMode.DAY)
        vm.previous()
        assertEquals(listOf(LocalDate.of(2026, 11, 13)), vm.uiState.value.columns.map { it.date })
        assertEquals("Fri, 13 November", vm.uiState.value.selectedLabel)
    }

    @Test
    fun `crossing into another month in week view loads that month`() {
        val vm = viewModel()
        vm.setMode(CalendarMode.WEEK)

        repeat(4) { vm.next() } // 5 Dec

        assertEquals("December", vm.uiState.value.monthLabel)
        coVerify(atLeast = 2) { calendar.agenda(any(), any()) }
    }

    @Test
    fun `opening a day from the week header switches to the day view on that date`() {
        val vm = viewModel()
        vm.setMode(CalendarMode.WEEK)

        vm.openDay(LocalDate.of(2026, 11, 9))

        assertEquals(CalendarMode.DAY, vm.uiState.value.mode)
        assertEquals(listOf(LocalDate.of(2026, 11, 9)), vm.uiState.value.columns.map { it.date })
    }

    // ---- week and day grids

    @Test
    fun `week view has seven columns of blocks in minutes since midnight, with lanes for overlaps`() {
        val vm = viewModel(
            listOf(
                item("a", "Review PR", "2026-11-07T09:00", "2026-11-07T10:30"),
                item("b", "Design crit", "2026-11-07T10:00", "2026-11-07T11:00", project = "p-des"),
                item("c", "Call", "2026-11-07T15:00", kind = AgendaKind.TASK, project = null),
                item("allday", "Release", "2026-11-07T00:00", "2026-11-07T23:59", allDay = true),
                item("mon", "Standup", "2026-11-02T09:30", "2026-11-02T10:00"),
            ),
        )

        vm.setMode(CalendarMode.WEEK)

        val state = vm.uiState.value
        assertTrue(state.cells.isEmpty())
        assertEquals(7, state.columns.size)
        assertEquals(LocalDate.of(2026, 11, 1), state.columns.first().date)
        assertEquals(listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"), state.columns.map { it.letter })
        val saturday = state.columns.single { it.isToday }
        assertTrue(saturday.isSelected)
        assertEquals(listOf("allday"), saturday.allDay.map { it.id })
        val a = saturday.blocks.single { it.id == "a" }
        val b = saturday.blocks.single { it.id == "b" }
        assertEquals(540, a.startMinute)
        assertEquals(90, a.durationMinutes)
        assertEquals(setOf(0, 1), setOf(a.lane, b.lane))
        assertEquals(2, a.laneCount)
        val task = saturday.blocks.single { it.id == "c" }
        assertEquals(900, task.startMinute)
        assertEquals(30, task.durationMinutes) // a task gets the minimum block
        assertEquals(570, state.columns.single { it.date == LocalDate.of(2026, 11, 2) }.blocks.single().startMinute)
    }

    @Test
    fun `the grid scrolls to an hour before the first entry and shows a now line only when today is visible`() {
        val vm = viewModel(listOf(item("a", "Flight", "2026-11-07T06:30", "2026-11-07T08:00")))

        vm.setMode(CalendarMode.DAY)
        assertEquals(5, vm.uiState.value.scrollHour)
        assertEquals(600, vm.uiState.value.nowMinute) // 10:00

        vm.next()
        assertNull(vm.uiState.value.nowMinute)
        assertEquals(7, vm.uiState.value.scrollHour) // nothing that day: the morning
    }

    @Test
    fun `a block that runs past midnight stops at the end of its day`() {
        val vm = viewModel(listOf(item("night", "Flight", "2026-11-07T23:00", "2026-11-08T02:00")))

        vm.setMode(CalendarMode.DAY)

        val block = vm.uiState.value.columns.single().blocks.single()
        assertEquals(1380, block.startMinute)
        assertEquals(60, block.durationMinutes)
    }

    // ---- schedule

    @Test
    fun `schedule lists only the days that have something, from the selected day on`() {
        val vm = viewModel(
            listOf(
                item("a", "Today thing", "2026-11-07T11:00"),
                item("b", "Next week", "2026-11-14T09:00"),
                item("c", "Reminder", "2026-11-14T08:00", kind = AgendaKind.REMINDER, project = null),
            ),
        )

        vm.setMode(CalendarMode.SCHEDULE)

        val schedule = vm.uiState.value.schedule
        assertEquals(listOf(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14)), schedule.map { it.date })
        assertEquals("Sat 7 Nov", schedule.first().label)
        assertTrue(schedule.first().isToday)
        assertEquals(listOf("c", "b"), schedule.last().rows.map { it.id }) // by time
        assertTrue(vm.uiState.value.cells.isEmpty())
    }

    @Test
    fun `schedule asks for a month ahead of the day it starts on`() {
        val to = slot<Instant>()
        coEvery { calendar.agenda(any(), capture(to)) } returns Result.success(emptyList())
        val vm = CalendarViewModel(calendar, projects, tasks, reminders, clock)

        vm.setMode(CalendarMode.SCHEDULE)
        vm.select(LocalDate.of(2026, 11, 30))

        assertTrue(to.captured >= ist("2026-12-31T00:00"))
    }

    // ---- creating

    private fun vmWithCreateStubs(): CalendarViewModel {
        coEvery { calendar.createEvent(any()) } returns Result.success("e1")
        coEvery { tasks.create(any()) } returns Result.success(Task("t1", "x", "", null, Priority.NORMAL, TaskStatus.TODO, null, null, false, false, null, emptyList()))
        coEvery { reminders.create(any()) } answers {
            val r = firstArg<NewReminder>()
            Result.success(Reminder("r1", null, r.title, r.remindAt, r.repeatRule, null, true, null))
        }
        return viewModel()
    }

    @Test
    fun `the plus button opens an event today at the next full hour, one hour long, with a 30 minute alert`() {
        val vm = vmWithCreateStubs()

        vm.openCreate()

        val draft = vm.uiState.value.create!!.draft
        assertEquals(CreateKind.EVENT, draft.kind)
        assertEquals(LocalDate.of(2026, 11, 7), draft.date)
        assertEquals(LocalTime.of(11, 0), draft.start)
        assertEquals(LocalTime.of(12, 0), draft.end)
        assertEquals(30, draft.alertMinutes)
    }

    @Test
    fun `tapping a slot opens the sheet on that day and half hour`() {
        val vm = vmWithCreateStubs()

        vm.openCreate(date = LocalDate.of(2026, 11, 10), time = LocalTime.of(14, 30))

        val draft = vm.uiState.value.create!!.draft
        assertEquals(LocalDate.of(2026, 11, 10), draft.date)
        assertEquals(LocalTime.of(14, 30), draft.start)
        assertEquals(LocalTime.of(15, 30), draft.end)
    }

    @Test
    fun `saving an event sends its times and alert, closes the sheet, selects its day and reloads`() {
        val vm = vmWithCreateStubs()
        val event = slot<NewEvent>()
        coEvery { calendar.createEvent(capture(event)) } returns Result.success("e1")
        vm.openCreate(date = LocalDate.of(2026, 11, 10), time = LocalTime.of(14, 30))
        vm.setTitle("  Dentist ")
        vm.setAlert(10)

        vm.save()

        assertEquals(NewEvent("Dentist", ist("2026-11-10T14:30"), ist("2026-11-10T15:30"), false, null, 10), event.captured)
        assertNull(vm.uiState.value.create)
        assertEquals("Tue, 10 November", vm.uiState.value.selectedLabel)
        coVerify(atLeast = 2) { calendar.agenda(any(), any()) }
    }

    @Test
    fun `an all day event covers the whole day`() {
        val vm = vmWithCreateStubs()
        val event = slot<NewEvent>()
        coEvery { calendar.createEvent(capture(event)) } returns Result.success("e1")
        vm.openCreate(date = LocalDate.of(2026, 11, 10))
        vm.setTitle("Holiday")
        vm.setAllDay(true)

        vm.save()

        assertEquals(ist("2026-11-10T00:00"), event.captured.start)
        assertEquals(ist("2026-11-11T00:00"), event.captured.end)
        assertTrue(event.captured.allDay)
    }

    @Test
    fun `saving a task makes it due at the chosen time, or 9 in the morning when it has none`() {
        val vm = vmWithCreateStubs()
        val tasksMade = mutableListOf<NewTask>()
        coEvery { tasks.create(capture(tasksMade)) } returns Result.success(Task("t1", "x", "", null, Priority.NORMAL, TaskStatus.TODO, null, null, false, false, null, emptyList()))

        vm.openCreate(CreateKind.TASK, LocalDate.of(2026, 11, 10), LocalTime.of(16, 0))
        vm.setTitle("Pay rent")
        vm.setRepeat("Every Month")
        vm.save()
        vm.openCreate(CreateKind.TASK, LocalDate.of(2026, 11, 11))
        vm.setTitle("Water plants")
        vm.setAllDay(true)
        vm.save()

        assertEquals(listOf("Pay rent", "Water plants"), tasksMade.map { it.title })
        assertEquals(ist("2026-11-10T16:00"), tasksMade[0].dueAt)
        assertEquals("Every Month", tasksMade[0].repeatRule)
        assertEquals(ist("2026-11-11T09:00"), tasksMade[1].dueAt)
        assertNull(tasksMade[1].repeatRule)
    }

    @Test
    fun `saving a reminder sets its time and repeat`() {
        val vm = vmWithCreateStubs()
        val made = slot<NewReminder>()
        coEvery { reminders.create(capture(made)) } returns Result.success(Reminder("r", null, "x", Instant.EPOCH, null, null, true, null))
        vm.openCreate(CreateKind.REMINDER, LocalDate.of(2026, 11, 10), LocalTime.of(8, 0))
        vm.setTitle("Stretch")
        vm.setRepeat("Every Day")

        vm.save()

        assertEquals(NewReminder(title = "Stretch", remindAt = ist("2026-11-10T08:00"), repeatRule = "Every Day"), made.captured)
    }

    @Test
    fun `a blank title or an end before the start is explained and nothing is saved`() {
        val vm = vmWithCreateStubs()
        vm.openCreate()

        vm.save()
        assertEquals("Give it a title", vm.uiState.value.create!!.error)

        vm.setTitle("Lunch")
        vm.setEnd(LocalTime.of(10, 0)) // start is 11:00
        vm.save()
        assertEquals("The end must be after the start", vm.uiState.value.create!!.error)
        coVerify(exactly = 0) { calendar.createEvent(any()) }
    }

    @Test
    fun `moving the start keeps the length, and switching kind keeps what was typed`() {
        val vm = vmWithCreateStubs()
        vm.openCreate(date = LocalDate.of(2026, 11, 10), time = LocalTime.of(9, 0))
        vm.setEnd(LocalTime.of(10, 30))
        vm.setTitle("Plan")

        vm.setStart(LocalTime.of(13, 0))
        vm.setKind(CreateKind.TASK)

        val draft = vm.uiState.value.create!!.draft
        assertEquals(LocalTime.of(14, 30), draft.end)
        assertEquals("Plan", draft.title)
        assertEquals(CreateKind.TASK, draft.kind)
    }

    @Test
    fun `a failed save keeps the sheet and what was typed, with the reason`() {
        val vm = vmWithCreateStubs()
        coEvery { calendar.createEvent(any()) } returns Result.failure(RuntimeException("offline"))
        vm.openCreate()
        vm.setTitle("Lunch")

        vm.save()

        val sheet = vm.uiState.value.create!!
        assertEquals("offline", sheet.error)
        assertEquals("Lunch", sheet.draft.title)
        assertEquals(CreateStatus.EDITING, sheet.status)
    }

    @Test
    fun `closing the sheet throws the draft away`() {
        val vm = vmWithCreateStubs()
        vm.openCreate()
        vm.setTitle("Never mind")

        vm.closeCreate()

        assertNull(vm.uiState.value.create)
    }

    // ---- looking at and deleting an entry

    @Test
    fun `an event opens a detail, a task does not`() {
        val vm = viewModel(
            listOf(
                item("ev", "Design review", "2026-11-07T14:00", "2026-11-07T15:00"),
                item("tk", "Pay rent", "2026-11-07T16:00", kind = AgendaKind.TASK, project = null),
            ),
        )

        vm.openDetail("tk")
        assertNull(vm.uiState.value.detail)
        vm.openDetail("ev")

        val detail = vm.uiState.value.detail!!
        assertEquals("Design review", detail.title)
        assertEquals(AgendaKind.EVENT, detail.kind)
        assertEquals("Sat 7 Nov · 2:00 PM", detail.whenLabel)
    }

    @Test
    fun `deleting an event or a reminder calls the right repository and reloads`() {
        val vm = viewModel(
            listOf(
                item("ev", "Design review", "2026-11-07T14:00", "2026-11-07T15:00"),
                item("rm", "Drink water", "2026-11-07T11:00", kind = AgendaKind.REMINDER, project = null),
            ),
        )
        coEvery { calendar.deleteEvent("ev") } returns Result.success(Unit)
        coEvery { reminders.delete("rm") } returns Result.success(Unit)

        vm.openDetail("ev")
        vm.deleteDetail()
        vm.openDetail("rm")
        vm.deleteDetail()

        coVerify(exactly = 1) { calendar.deleteEvent("ev") }
        coVerify(exactly = 1) { reminders.delete("rm") }
        assertNull(vm.uiState.value.detail)
        coVerify(atLeast = 3) { calendar.agenda(any(), any()) }
    }

    @Test
    fun `a failed delete keeps the detail open with the reason`() {
        val vm = viewModel(listOf(item("ev", "Design review", "2026-11-07T14:00", "2026-11-07T15:00")))
        coEvery { calendar.deleteEvent(any()) } returns Result.failure(RuntimeException("offline"))

        vm.openDetail("ev")
        vm.deleteDetail()

        assertEquals("offline", vm.uiState.value.detail!!.error)
        assertFalse(vm.uiState.value.detail!!.deleting)
    }

    @Test
    fun `a quiet reload that fails keeps what is on screen`() = runTest {
        val vm = viewModel(listOf(item("a", "A", "2026-11-07T09:00")))
        coEvery { calendar.agenda(any(), any()) } returns Result.failure(RuntimeException("offline"))

        vm.reload()

        assertNull(vm.uiState.value.error)
        assertEquals(35, vm.uiState.value.cells.size)
    }
}
