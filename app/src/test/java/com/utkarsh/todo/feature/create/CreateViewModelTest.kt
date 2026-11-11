package com.utkarsh.todo.feature.create

import androidx.lifecycle.SavedStateHandle
import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import com.utkarsh.todo.feature.calendar.domain.NewEvent
import com.utkarsh.todo.feature.create.domain.CreateType
import com.utkarsh.todo.feature.create.ui.CreateViewModel
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.reminders.domain.AlertStyle
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
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
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-12T04:50:00Z"), zone) // Thu 12 Nov 2026, 10:20 IST
    private val tasks = mockk<TasksRepository>()
    private val calendar = mockk<CalendarRepository>()
    private val reminders = mockk<RemindersRepository>()
    private val projects = mockk<ProjectsRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { projects.projects() } returns Result.success(listOf(Project("p1", "Design", "#935EDF")))
        coEvery { tasks.create(any()) } answers { Result.success(task(firstArg<NewTask>())) }
        coEvery { calendar.createEvent(any()) } returns Result.success("e1")
        coEvery { reminders.create(any()) } returns Result.failure(RuntimeException("not under test"))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun task(n: NewTask) = Task("t1", n.title, "", n.projectId, n.priority, TaskStatus.TODO, n.dueAt, null, false, false, n.repeatRule, emptyList())

    private fun viewModel() = CreateViewModel(tasks, calendar, reminders, projects, clock, SavedStateHandle())

    @Test
    fun `starts as a task for today with no time and loads the projects`() {
        val state = viewModel().uiState.value
        assertEquals(CreateType.TASK, state.form.type)
        assertEquals(LocalDate.of(2026, 11, 12), state.form.date)
        assertNull(state.form.time)
        assertEquals(listOf("Design"), state.projects.map { it.name })
    }

    @Test
    fun `chips edit the form`() {
        val vm = viewModel()
        vm.setTitle("Pay rent")
        vm.setDate(LocalDate.of(2026, 11, 14))
        vm.setTime(LocalTime.of(18, 0))
        vm.toggleAlert(30)
        vm.toggleAlert(60)
        vm.toggleAlert(30) // off again
        vm.addAlert(45)
        vm.addAlert(45) // adding twice keeps one
        vm.setRepeat("Every Week")
        vm.setPriority(Priority.URGENT)
        vm.toggleProject("p1")

        val form = vm.uiState.value.form
        assertEquals("Pay rent", form.title)
        assertEquals(LocalDate.of(2026, 11, 14), form.date)
        assertEquals(LocalTime.of(18, 0), form.time)
        assertEquals(setOf(60, 45), form.alerts)
        assertEquals("Every Week", form.repeat)
        assertEquals(Priority.URGENT, form.priority)
        assertEquals("p1", form.projectId)

        vm.toggleProject("p1")
        assertNull(vm.uiState.value.form.projectId)
        vm.setTime(null)
        assertNull(vm.uiState.value.form.time)
    }

    @Test
    fun `an event always keeps a time, and the length chips set the length or all day`() {
        val vm = viewModel()
        vm.setType(CreateType.EVENT)
        val start = vm.uiState.value.form.time
        vm.setTime(null)
        assertEquals(start, vm.uiState.value.form.time)

        vm.setDuration(120)
        assertEquals(120, vm.uiState.value.form.durationMinutes)
        vm.setAllDay()
        assertTrue(vm.uiState.value.form.allDay)
        vm.setDuration(30)
        assertFalse(vm.uiState.value.form.allDay)
    }

    @Test
    fun `saving without a title says so and saves nothing`() = runTest {
        val vm = viewModel()
        vm.save()
        assertEquals("Give it a title", vm.uiState.value.error)
        coVerify(exactly = 0) { tasks.create(any()) }
    }

    @Test
    fun `saving a task creates it with its fields and one reminder per alert that is still ahead`() = runTest {
        val vm = viewModel()
        vm.setTitle("  Pay rent  ")
        vm.setDate(LocalDate.of(2026, 11, 12))
        vm.setTime(LocalTime.of(18, 0))
        vm.toggleAlert(0)
        vm.toggleAlert(60)
        vm.toggleAlert(1440) // yesterday evening: already past, skipped
        vm.setRepeat("Every Week")
        vm.setPriority(Priority.HIGH)
        vm.toggleProject("p1")

        vm.save()

        val created = slot<NewTask>()
        coVerify { tasks.create(capture(created)) }
        assertEquals(NewTask("Pay rent", "", "p1", Priority.HIGH, Instant.parse("2026-11-12T12:30:00Z"), "Every Week", emptyList()), created.captured)
        coVerify(exactly = 1) { reminders.create(NewReminder("Pay rent", Instant.parse("2026-11-12T12:30:00Z"), "t1", "Every Week", style = AlertStyle.ALARM)) }
        coVerify(exactly = 1) { reminders.create(NewReminder("Pay rent", Instant.parse("2026-11-12T11:30:00Z"), "t1", "Every Week", style = AlertStyle.ALARM)) }
        coVerify(exactly = 2) { reminders.create(any()) }
    }

    @Test
    fun `alerts are alarms that take over the screen unless notification is chosen, and notes are kept with the task`() = runTest {
        val vm = viewModel()
        assertEquals(AlertStyle.ALARM, vm.uiState.value.form.alertStyle)
        vm.setTitle("Call bank")
        vm.setNotes("  ask about the card  ")
        vm.setTime(LocalTime.of(18, 0))
        vm.toggleAlert(0)
        vm.setAlertStyle(AlertStyle.NOTIFICATION)

        vm.save()

        val created = slot<NewTask>()
        coVerify { tasks.create(capture(created)) }
        assertEquals("ask about the card", created.captured.notes)
        coVerify(exactly = 1) { reminders.create(match { it.style == AlertStyle.NOTIFICATION && it.taskId == "t1" }) }
    }

    @Test
    fun `an event passes the alert style to the calendar`() = runTest {
        val vm = viewModel()
        vm.setType(CreateType.EVENT)
        vm.setTitle("Standup")
        vm.setAlertStyle(AlertStyle.NOTIFICATION)
        vm.save()
        val event = slot<NewEvent>()
        coVerify { calendar.createEvent(capture(event)) }
        assertEquals(AlertStyle.NOTIFICATION, event.captured.alertStyle)
    }

    @Test
    fun `a saved task closes the screen`() = runTest {
        val vm = viewModel()
        var closed = 0
        backgroundScope.launch(Dispatchers.Unconfined) { vm.saved.collect { closed++ } }
        vm.setTitle("Water plants")
        vm.save()
        assertEquals(1, closed)
    }

    @Test
    fun `saving an event sends its times, project and alerts to the calendar`() = runTest {
        val vm = viewModel()
        vm.setType(CreateType.EVENT)
        vm.setTitle("Lunch with Sam")
        vm.setDate(LocalDate.of(2026, 11, 13))
        vm.setTime(LocalTime.of(12, 0))
        vm.setDuration(90)
        vm.toggleProject("p1")
        vm.toggleAlert(10)

        vm.save()

        val event = slot<NewEvent>()
        coVerify { calendar.createEvent(capture(event)) }
        assertEquals("Lunch with Sam", event.captured.title)
        assertEquals(Instant.parse("2026-11-13T06:30:00Z"), event.captured.start)
        assertEquals(Instant.parse("2026-11-13T08:00:00Z"), event.captured.end)
        assertEquals("p1", event.captured.projectId)
        assertEquals(listOf(30, 10), event.captured.alerts)
        coVerify(exactly = 0) { reminders.create(any()) } // the calendar makes an event's alerts itself
    }

    @Test
    fun `a failed save shows the message and can be tried again`() = runTest {
        coEvery { tasks.create(any()) } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel()
        vm.setTitle("Pay rent")
        vm.save()
        assertEquals("offline", vm.uiState.value.error)
        assertFalse(vm.uiState.value.saving)

        coEvery { tasks.create(any()) } answers { Result.success(task(firstArg<NewTask>())) }
        vm.save()
        assertNull(vm.uiState.value.error)
        coVerify(exactly = 2) { tasks.create(any()) }
    }

    @Test
    fun `an event carries its place, video link, guests and busy or free`() = runTest {
        val vm = viewModel()
        vm.setType(CreateType.EVENT)
        vm.setTitle("Design sync")
        vm.setLocation("  Room 4 ")
        vm.setVideoLink("https://meet.example/abc")
        vm.addGuest(" sam@example.com ")
        vm.addGuest("SAM@example.com") // the same guest again
        vm.addGuest("   ")
        vm.addGuest("priya@example.com")
        vm.removeGuest("priya@example.com")
        vm.setBusy(false)

        vm.save()

        val event = slot<NewEvent>()
        coVerify { calendar.createEvent(capture(event)) }
        assertEquals("Room 4", event.captured.location)
        assertEquals("https://meet.example/abc", event.captured.videoLink)
        assertEquals(listOf("sam@example.com"), event.captured.guests)
        assertEquals(false, event.captured.busy)
    }

    @Test
    fun `blank place and video link are sent as nothing`() = runTest {
        val vm = viewModel()
        vm.setType(CreateType.EVENT)
        vm.setTitle("Quiet")
        vm.setLocation("   ")
        vm.save()
        val event = slot<NewEvent>()
        coVerify { calendar.createEvent(capture(event)) }
        assertNull(event.captured.location)
        assertNull(event.captured.videoLink)
        assertEquals(true, event.captured.busy)
    }
}
