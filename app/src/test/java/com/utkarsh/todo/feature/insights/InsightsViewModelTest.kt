package com.utkarsh.todo.feature.insights

import com.utkarsh.todo.feature.insights.domain.Heatmap
import com.utkarsh.todo.feature.insights.domain.HeatmapDay
import com.utkarsh.todo.feature.insights.domain.HeatmapSummary
import com.utkarsh.todo.feature.insights.domain.InsightsRepository
import com.utkarsh.todo.feature.insights.domain.heatLevel
import com.utkarsh.todo.feature.insights.ui.HEATMAP_WEEKS
import com.utkarsh.todo.feature.insights.ui.InsightsViewModel
import com.utkarsh.todo.core.offline.CacheFirst
import io.mockk.coEvery
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
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
class InsightsViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), zone) // Saturday 7 Nov 2026
    private val today = LocalDate.of(2026, 11, 7)
    private val firstDay = LocalDate.of(2026, 7, 5) // Sunday, 17 weeks before this week's Sunday (1 Nov)
    private val repository = mockk<InsightsRepository>()
    private val tasks = mockk<TasksRepository>()
    private val taskChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { tasks.changes } returns taskChanges
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun heatmap(counts: Map<LocalDate, Int> = emptyMap(), rhythm: List<Double> = List(7) { 0.0 }) = Heatmap(
        generateSequence(firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.map { HeatmapDay(it, counts[it] ?: 0) }.toList(),
        HeatmapSummary(total = counts.values.sum(), streak = 2, best = counts.values.maxOrNull() ?: 0, rhythm = rhythm),
    )

    private fun viewModel(data: Heatmap = heatmap()): InsightsViewModel {
        coEvery { repository.heatmap(any(), any(), any()) } returns Result.success(data)
        return InsightsViewModel(repository, tasks, clock)
    }

    // --- saved copy first -------------------------------------------------------------------------------------------

    private fun savedThenServer(saved: Result<Heatmap>, gate: CompletableDeferred<Result<Heatmap>>) {
        coEvery { repository.heatmap(any(), any(), any()) } coAnswers { if (CacheFirst.isActive()) saved else gate.await() }
    }

    @Test
    fun `the saved numbers show at once and the server's numbers then replace them`() = runTest {
        val gate = CompletableDeferred<Result<Heatmap>>()
        savedThenServer(Result.success(heatmap(mapOf(today to 1))), gate)

        val vm = InsightsViewModel(repository, tasks, clock)

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("1 tasks done", vm.uiState.value.selectedValue)

        gate.complete(Result.success(heatmap(mapOf(today to 4))))
        assertEquals("4 tasks done", vm.uiState.value.selectedValue)
    }

    @Test
    fun `with nothing saved Insights keeps loading until the server answers, without an error`() = runTest {
        val gate = CompletableDeferred<Result<Heatmap>>()
        savedThenServer(Result.failure(RuntimeException("HTTP 504")), gate)

        val vm = InsightsViewModel(repository, tasks, clock)

        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
        gate.complete(Result.success(heatmap(mapOf(today to 2))))
        assertEquals("2 tasks done", vm.uiState.value.selectedValue)
    }

    @Test
    fun `a later reload goes straight to the server and never replays the saved copy`() = runTest {
        val modes = mutableListOf<Boolean>()
        coEvery { repository.heatmap(any(), any(), any()) } coAnswers { modes += CacheFirst.isActive(); Result.success(heatmap()) }

        val vm = InsightsViewModel(repository, tasks, clock)
        vm.refresh()

        assertEquals(listOf(true, false, false), modes)
    }

    @Test
    fun `finishing or adding a task elsewhere refreshes the numbers without bringing the skeleton back`() {
        val vm = viewModel(heatmap(mapOf(today to 1)))
        assertEquals("1 tasks done", vm.uiState.value.selectedValue)

        var loadingDuringRefresh: Boolean? = null
        coEvery { repository.heatmap(any(), any(), any()) } coAnswers {
            loadingDuringRefresh = vm.uiState.value.isLoading
            Result.success(heatmap(mapOf(today to 3)))
        }
        taskChanges.tryEmit(Unit)

        assertEquals("3 tasks done", vm.uiState.value.selectedValue)
        assertEquals(false, loadingDuringRefresh)
    }

    @Test
    fun `heat level bands for the number of tasks done`() {
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4, 4), listOf(0, 1, 2, 3, 4, 5, 6, 7, 20).map { heatLevel(it) })
        assertEquals(0, heatLevel(-3))
    }

    @Test
    fun `requests eighteen weeks ending today with the device offset`() {
        viewModel()

        coVerify { repository.heatmap(firstDay, today, 330) } // IST is UTC+5:30
    }

    @Test
    fun `builds eighteen sunday-first week columns and marks days after today as future`() {
        val state = viewModel().uiState.value

        assertEquals(HEATMAP_WEEKS, state.weeks.size)
        assertTrue(state.weeks.all { it.size == 7 })
        assertEquals(firstDay, state.weeks.first().first().date)
        val lastWeek = state.weeks.last()
        assertEquals(LocalDate.of(2026, 11, 1), lastWeek.first().date)
        assertFalse(lastWeek[6].isFuture) // Saturday 7 Nov is today
        assertEquals(today, lastWeek[6].date)
        assertTrue(state.weeks.flatten().none { it.isFuture })
    }

    @Test
    fun `today is selected initially and the summary shows its count`() {
        val state = viewModel(heatmap(counts = mapOf(today to 3, LocalDate.of(2026, 11, 6) to 5))).uiState.value

        assertEquals("Sat 7 Nov", state.selectedLabel)
        assertEquals("3 tasks done", state.selectedValue)
        assertEquals(today, state.weeks.flatten().single { it.isSelected }.date)
        assertEquals(8, state.total)
        assertEquals(5, state.best)
        assertEquals(2, state.streak)
    }

    @Test
    fun `selecting a past day updates the label but ignores future and out of range days`() {
        val vm = viewModel(heatmap(counts = mapOf(LocalDate.of(2026, 11, 3) to 6)))

        vm.select(LocalDate.of(2026, 11, 3))
        assertEquals("Tue 3 Nov", vm.uiState.value.selectedLabel)
        assertEquals("6 tasks done", vm.uiState.value.selectedValue)
        assertEquals(3, vm.uiState.value.weeks.flatten().single { it.date == LocalDate.of(2026, 11, 3) }.level)

        vm.select(LocalDate.of(2026, 11, 8)) // tomorrow
        vm.select(LocalDate.of(2026, 1, 1)) // before the window
        assertEquals("Tue 3 Nov", vm.uiState.value.selectedLabel)
    }

    @Test
    fun `month labels appear on the first week of each month only`() {
        val labels = viewModel().uiState.value.monthLabels

        assertEquals(HEATMAP_WEEKS, labels.size)
        assertEquals("Jul", labels.first()) // week of 5 Jul starts on day <= 7
        assertTrue(labels.filter { it.isNotEmpty() }.toSet().containsAll(listOf("Aug", "Sep", "Oct", "Nov")))
    }

    @Test
    fun `weekly rhythm is scaled to the busiest weekday and flags the peak`() {
        val state = viewModel(heatmap(rhythm = listOf(1.0, 4.0, 2.0, 0.0, 4.0, 1.0, 0.5))).uiState.value

        assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), state.rhythm.map { it.letter })
        assertEquals(1f, state.rhythm[1].fraction, 0.001f)
        assertEquals(0.5f, state.rhythm[2].fraction, 0.001f)
        assertEquals(listOf(false, true, false, false, true, false, false), state.rhythm.map { it.isPeak })
    }

    @Test
    fun `an all-zero rhythm has no peak and no division by zero`() {
        val state = viewModel().uiState.value
        assertTrue(state.rhythm.all { it.fraction == 0f && !it.isPeak })
    }

    @Test
    fun `load failure is reported and refresh recovers`() {
        coEvery { repository.heatmap(any(), any(), any()) } returns Result.failure(RuntimeException("offline"))
        val vm = InsightsViewModel(repository, tasks, clock)
        assertEquals("offline", vm.uiState.value.error)
        assertTrue(vm.uiState.value.weeks.isEmpty())

        coEvery { repository.heatmap(any(), any(), any()) } returns Result.success(heatmap())
        vm.refresh()

        assertNull(vm.uiState.value.error)
        assertEquals(HEATMAP_WEEKS, vm.uiState.value.weeks.size)
    }
}
