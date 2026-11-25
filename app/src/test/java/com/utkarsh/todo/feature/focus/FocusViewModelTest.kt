package com.utkarsh.todo.feature.focus

import androidx.lifecycle.SavedStateHandle
import com.utkarsh.todo.feature.focus.domain.FocusAlarm
import com.utkarsh.todo.feature.focus.ui.FocusPhase
import com.utkarsh.todo.feature.focus.ui.FocusUiState
import com.utkarsh.todo.feature.focus.ui.FocusViewModel
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A clock that moves exactly as far as the test's virtual time has moved. */
@OptIn(ExperimentalCoroutinesApi::class)
private class TestClock(private val scheduler: TestCoroutineScheduler) : Clock() {
    private val start = Instant.parse("2026-11-07T05:30:00Z")
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = start.plusMillis(scheduler.currentTime)
}

/** Remembers what the timer asked the system to do when time is up. */
private class RecordingAlarm : FocusAlarm {
    val scheduled = mutableListOf<Pair<Instant, String>>()
    var cancels = 0
    override fun schedule(at: Instant, title: String) { scheduled += at to title }
    override fun cancel() { cancels += 1 }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val tasks = mockk<TasksRepository>()
    private val alarm = RecordingAlarm()

    private fun task(done: Boolean = false) = Task(
        "t1", "Write the report", "", null, Priority.NORMAL, if (done) TaskStatus.DONE else TaskStatus.TODO, null, null, false, false, null, emptyList(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { tasks.get("t1") } returns Result.success(task())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = FocusViewModel(SavedStateHandle(mapOf("taskId" to "t1")), tasks, TestClock(testScheduler), alarm).also { runCurrent() }

    // --- the system tells you when time is up, even with the app closed ---------------------------------------------

    @Test
    fun `starting asks the system for an alarm at the end time, named after the task`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(); runCurrent()

        assertEquals(listOf(Instant.parse("2026-11-07T05:55:00Z") to "Write the report"), alarm.scheduled)
    }

    @Test
    fun `a free timer's alarm is just called Focus time`() = runTest(dispatcher) {
        val vm = FocusViewModel(SavedStateHandle(), tasks, TestClock(testScheduler), alarm).also { runCurrent() }
        vm.start(); runCurrent()

        assertEquals("Focus time", alarm.scheduled.single().second)
    }

    @Test
    fun `pausing cancels the alarm and resuming sets it again for the new end time`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        vm.pause(); runCurrent()
        assertEquals(1, alarm.cancels)

        advanceTimeBy(120_000); runCurrent() // paused for two minutes
        vm.start(); runCurrent()
        assertEquals(Instant.parse("2026-11-07T05:57:00Z"), alarm.scheduled.last().first) // resumed at 05:33 with 24:00 left
        assertEquals(2, alarm.scheduled.size)
    }

    @Test
    fun `resetting cancels the alarm`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(); runCurrent()
        vm.reset(); runCurrent()

        assertEquals(1, alarm.cancels)
    }

    @Test
    fun `when the time runs out with the screen open the alarm is cancelled, so it does not ping twice`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(); runCurrent()
        advanceTimeBy(25 * 60_000L + 1_000); runCurrent()

        assertEquals(FocusPhase.FINISHED, vm.uiState.value.phase)
        assertEquals(1, alarm.cancels)
    }

    @Test
    fun `starts ready with 25 minutes and the task's title`() = runTest(dispatcher) {
        val state = viewModel().uiState.value
        assertEquals(FocusPhase.IDLE, state.phase)
        assertEquals("25:00", state.clockLabel)
        assertEquals("Write the report", state.taskTitle)
        assertFalse(state.taskDone)
    }

    @Test
    fun `without a task it is a free timer, with no lookup and nothing to mark done`() = runTest(dispatcher) {
        val vm = FocusViewModel(SavedStateHandle(), tasks, TestClock(testScheduler), alarm).also { runCurrent() }

        assertFalse(vm.uiState.value.hasTask)
        assertNull(vm.uiState.value.taskTitle)

        vm.start(); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(FocusPhase.RUNNING, vm.uiState.value.phase)
        assertEquals("24:50", vm.uiState.value.clockLabel)

        vm.markTaskDone(); runCurrent()
        coVerify(exactly = 0) { tasks.get(any()) }
        coVerify(exactly = 0) { tasks.setDone(any(), any()) }
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `a blank task id is also a free timer`() = runTest(dispatcher) {
        val vm = FocusViewModel(SavedStateHandle(mapOf("taskId" to "")), tasks, TestClock(testScheduler), alarm).also { runCurrent() }
        assertFalse(vm.uiState.value.hasTask)
        coVerify(exactly = 0) { tasks.get(any()) }
    }

    @Test
    fun `with a task the screen knows it has one`() = runTest(dispatcher) {
        assertTrue(viewModel().uiState.value.hasTask)
    }

    @Test
    fun `the clock label pads minutes and seconds`() {
        assertEquals("05:09", FocusUiState(remainingSeconds = 309).clockLabel)
        assertEquals("00:00", FocusUiState(remainingSeconds = 0).clockLabel)
    }

    @Test
    fun `counts down while running`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(); runCurrent()
        assertEquals(FocusPhase.RUNNING, vm.uiState.value.phase)
        assertEquals("25:00", vm.uiState.value.clockLabel)

        advanceTimeBy(10_000); runCurrent()
        assertEquals("24:50", vm.uiState.value.clockLabel)
        assertTrue(vm.uiState.value.progress > 0f)
    }

    @Test
    fun `pausing freezes the clock and resume carries on from there`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        vm.pause()
        assertEquals(FocusPhase.PAUSED, vm.uiState.value.phase)
        assertEquals("24:00", vm.uiState.value.clockLabel)

        advanceTimeBy(5 * 60_000); runCurrent() // time passes while paused
        assertEquals("24:00", vm.uiState.value.clockLabel)

        vm.start(); runCurrent()
        advanceTimeBy(30_000); runCurrent()
        assertEquals(FocusPhase.RUNNING, vm.uiState.value.phase)
        assertEquals("23:30", vm.uiState.value.clockLabel)
    }

    @Test
    fun `finishes at zero and stops ticking`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setMinutes(15)
        vm.start(); runCurrent()
        advanceTimeBy(15 * 60_000L + 1); runCurrent()
        val state = vm.uiState.value
        assertEquals(FocusPhase.FINISHED, state.phase)
        assertEquals("00:00", state.clockLabel)
        assertEquals(1f, state.progress, 0f)
    }

    @Test
    fun `another round after finishing starts a full one`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setMinutes(15)
        vm.start(); runCurrent()
        advanceTimeBy(15 * 60_000L + 1); runCurrent()
        vm.start(); runCurrent()
        assertEquals(FocusPhase.RUNNING, vm.uiState.value.phase)
        assertEquals("15:00", vm.uiState.value.clockLabel)
    }

    @Test
    fun `reset goes back to the full length`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(); runCurrent()
        advanceTimeBy(120_000); runCurrent()
        vm.reset()
        assertEquals(FocusPhase.IDLE, vm.uiState.value.phase)
        assertEquals("25:00", vm.uiState.value.clockLabel)
        advanceTimeBy(60_000); runCurrent()
        assertEquals("25:00", vm.uiState.value.clockLabel) // the ticker is gone
    }

    @Test
    fun `the length can be chosen only when nothing is running, and only from the presets`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setMinutes(45)
        assertEquals("45:00", vm.uiState.value.clockLabel)
        vm.setMinutes(7) // not a preset
        assertEquals(45, vm.uiState.value.minutes)

        vm.start(); runCurrent()
        vm.setMinutes(15)
        assertEquals(45, vm.uiState.value.minutes)
        vm.pause()
        vm.setMinutes(15)
        assertEquals(45, vm.uiState.value.minutes)
    }

    @Test
    fun `starting twice does not make two tickers`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(); vm.start(); runCurrent()
        advanceTimeBy(4_000); runCurrent()
        assertEquals("24:56", vm.uiState.value.clockLabel)
    }

    @Test
    fun `pause while not running does nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.pause()
        assertEquals(FocusPhase.IDLE, vm.uiState.value.phase)
    }

    @Test
    fun `marking the task done saves it, and a failure says so`() = runTest(dispatcher) {
        coEvery { tasks.setDone("t1", true) } returns Result.failure(RuntimeException("x"))
        val vm = viewModel()
        vm.markTaskDone(); runCurrent()
        assertEquals("Couldn't mark the task done", vm.uiState.value.message)
        assertFalse(vm.uiState.value.taskDone)

        coEvery { tasks.setDone("t1", true) } returns Result.success(task(done = true))
        vm.markTaskDone(); runCurrent()
        assertTrue(vm.uiState.value.taskDone)
        assertNull(vm.uiState.value.message)
        coVerify(exactly = 2) { tasks.setDone("t1", true) }
    }

    @Test
    fun `a task that could not be loaded still lets you focus`() = runTest(dispatcher) {
        coEvery { tasks.get("t1") } returns Result.failure(RuntimeException("offline"))
        val vm = viewModel()
        assertNull(vm.uiState.value.taskTitle)
        vm.start(); runCurrent()
        assertEquals(FocusPhase.RUNNING, vm.uiState.value.phase)
    }
}
