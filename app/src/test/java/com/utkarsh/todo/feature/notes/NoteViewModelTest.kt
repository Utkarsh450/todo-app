package com.utkarsh.todo.feature.notes

import com.utkarsh.todo.feature.notes.domain.NotesRepository
import com.utkarsh.todo.feature.notes.ui.NoteViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), ZoneId.of("Asia/Kolkata"))
    private val today = LocalDate.of(2026, 11, 7)
    private val repository = mockk<NotesRepository>()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads today's saved note`() = runTest {
        coEvery { repository.note(today) } returns Result.success("Ask Sam about Room")

        val vm = NoteViewModel(repository, clock)
        runCurrent()

        assertEquals("Ask Sam about Room", vm.uiState.value.text)
        assertTrue(vm.uiState.value.isLoaded)
    }

    @Test
    fun `rapid typing is saved once after the pause with the latest text`() = runTest {
        coEvery { repository.note(today) } returns Result.success("")
        coEvery { repository.save(today, any()) } returns Result.success(Unit)
        val vm = NoteViewModel(repository, clock)
        runCurrent()

        vm.onTextChange("A")
        advanceTimeBy(300)
        vm.onTextChange("Ab")
        advanceTimeBy(300)
        vm.onTextChange("Abc")
        advanceTimeBy(NoteViewModel.SAVE_DELAY_MS + 1)
        runCurrent()

        coVerify(exactly = 1) { repository.save(today, any()) }
        coVerify { repository.save(today, "Abc") }
        assertFalse(vm.uiState.value.saving)
    }

    @Test
    fun `text typed before the load finishes is not overwritten by the loaded note`() = runTest {
        coEvery { repository.note(today) } returns Result.success("old text")
        coEvery { repository.save(today, any()) } returns Result.success(Unit)
        val vm = NoteViewModel(repository, clock)

        vm.onTextChange("typed first") // before runCurrent: load has not completed
        runCurrent()

        assertEquals("typed first", vm.uiState.value.text)
    }

    @Test
    fun `a failed save is reported and cleared by further typing`() = runTest {
        coEvery { repository.note(today) } returns Result.success("")
        coEvery { repository.save(today, any()) } returns Result.failure(RuntimeException("offline"))
        val vm = NoteViewModel(repository, clock)
        runCurrent()

        vm.onTextChange("x")
        advanceTimeBy(NoteViewModel.SAVE_DELAY_MS + 1)
        runCurrent()
        assertEquals("Couldn't save note", vm.uiState.value.error)

        vm.onTextChange("xy")
        assertEquals(null, vm.uiState.value.error)
    }

    @Test
    fun `load failure is reported but editing stays possible`() = runTest {
        coEvery { repository.note(today) } returns Result.failure(RuntimeException("offline"))
        val vm = NoteViewModel(repository, clock)
        runCurrent()

        assertEquals("offline", vm.uiState.value.error)
        assertTrue(vm.uiState.value.isLoaded)
    }
}
