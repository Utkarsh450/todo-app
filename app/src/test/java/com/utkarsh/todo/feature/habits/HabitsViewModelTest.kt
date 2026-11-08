package com.utkarsh.todo.feature.habits

import com.utkarsh.todo.feature.habits.domain.Habit
import com.utkarsh.todo.feature.habits.domain.HabitsRepository
import com.utkarsh.todo.feature.habits.ui.HabitsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), zone) // Saturday 7 Nov
    private val repository = mockk<HabitsRepository>()
    private val sun = LocalDate.of(2026, 11, 1)
    private val sat = LocalDate.of(2026, 11, 7)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(vararg habits: Habit): HabitsViewModel {
        coEvery { repository.habits(sun, sat) } returns Result.success(habits.toList())
        return HabitsViewModel(repository, clock)
    }

    @Test
    fun `maps kept days onto the sunday to saturday week and counts today`() {
        val vm = viewModel(
            Habit("h1", "Read 20 min", setOf(sun, LocalDate.of(2026, 11, 4), sat)),
            Habit("h2", "Drink 2L water", setOf(LocalDate.of(2026, 11, 2))),
        )

        val state = vm.uiState.value
        assertEquals(6, state.todayIndex) // Saturday is the last column
        assertEquals(listOf(true, false, false, true, false, false, true), state.rows[0].kept)
        assertEquals(3, state.rows[0].keptCount)
        assertEquals(1, state.keptToday)
        assertEquals("1/2 today", state.summary)
    }

    @Test
    fun `toggling a day marks it kept immediately and persists`() {
        coEvery { repository.setKept("h1", sat, true) } returns Result.success(Unit)
        val vm = viewModel(Habit("h1", "Read", emptySet()))

        vm.toggle("h1", 6)

        assertEquals(true, vm.uiState.value.rows.single().kept[6])
        assertEquals("1/1 today", vm.uiState.value.summary)
        coVerify { repository.setKept("h1", sat, true) }
    }

    @Test
    fun `toggling a kept day clears it`() {
        coEvery { repository.setKept("h1", sat, false) } returns Result.success(Unit)
        val vm = viewModel(Habit("h1", "Read", setOf(sat)))

        vm.toggle("h1", 6)

        assertEquals(false, vm.uiState.value.rows.single().kept[6])
        coVerify { repository.setKept("h1", sat, false) }
    }

    @Test
    fun `a failed toggle reports the error and reloads the server state`() {
        coEvery { repository.setKept("h1", sat, true) } returns Result.failure(RuntimeException("x"))
        val vm = viewModel(Habit("h1", "Read", emptySet()))

        vm.toggle("h1", 6)

        assertEquals(false, vm.uiState.value.rows.single().kept[6]) // reloaded truth: not kept
        coVerify(exactly = 2) { repository.habits(sun, sat) }
    }

    @Test
    fun `adding a habit trims the name and appends a row`() {
        coEvery { repository.create("No phone after 11") } returns Result.success(Habit("h3", "No phone after 11", emptySet()))
        val vm = viewModel()

        vm.add("  No phone after 11 ")
        vm.add("   ")

        assertEquals(listOf("No phone after 11"), vm.uiState.value.rows.map { it.name })
        coVerify(exactly = 1) { repository.create(any()) }
    }

    @Test
    fun `out of range day index is ignored`() {
        val vm = viewModel(Habit("h1", "Read", emptySet()))
        vm.toggle("h1", 9)
        coVerify(exactly = 0) { repository.setKept(any(), any(), any()) }
    }

    @Test
    fun `load failure is reported`() {
        coEvery { repository.habits(any(), any()) } returns Result.failure(RuntimeException("offline"))
        assertEquals("offline", HabitsViewModel(repository, clock).uiState.value.error)
    }
}
