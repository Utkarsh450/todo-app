package com.utkarsh.todo.feature.memory

import com.utkarsh.todo.core.offline.CacheFirst
import com.utkarsh.todo.feature.ai.domain.AiException
import com.utkarsh.todo.feature.ai.domain.AiRepository
import com.utkarsh.todo.feature.ai.domain.MemoryFact
import com.utkarsh.todo.feature.memory.ui.MemoryViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
class MemoryViewModelTest {
    private val ai = mockk<AiRepository>()
    private val wakes = MemoryFact("f1", "Wakes at 7", "ROUTINE")
    private val tea = MemoryFact("f2", "Likes tea", "PREFERENCE")
    private val anita = MemoryFact("f3", "Sister Anita visits on Sundays", "PERSON")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { ai.memories() } returns Result.success(listOf(wakes, tea))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MemoryViewModel(ai)

    @Test
    fun `shows the saved list at once and the server's list when it arrives`() {
        val gate = CompletableDeferred<Result<List<MemoryFact>>>()
        coEvery { ai.memories() } coAnswers { if (CacheFirst.isActive()) Result.success(listOf(wakes)) else gate.await() }

        val vm = viewModel()
        assertEquals(listOf(wakes), vm.uiState.value.facts)
        assertTrue(vm.uiState.value.isLoading) // the real answer is still on its way

        gate.complete(Result.success(listOf(wakes, tea)))

        assertEquals(listOf(wakes, tea), vm.uiState.value.facts)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `facts are grouped by category in a fixed order and empty groups are left out`() {
        coEvery { ai.memories() } returns Result.success(listOf(tea, anita, wakes))

        val groups = viewModel().uiState.value.groups

        assertEquals(listOf("Routines", "Preferences", "People"), groups.map { it.first })
        assertEquals(listOf(listOf(wakes), listOf(tea), listOf(anita)), groups.map { it.second })
    }

    @Test
    fun `a failed load shows an error when there is nothing to show, and keeps saved facts otherwise`() {
        coEvery { ai.memories() } returns Result.failure(AiException("Can't reach the server."))
        val empty = viewModel().uiState.value
        assertEquals("Can't reach the server.", empty.error)
        assertFalse(empty.isLoading)

        coEvery { ai.memories() } coAnswers { if (CacheFirst.isActive()) Result.success(listOf(wakes)) else Result.failure(AiException("Can't reach the server.")) }
        val kept = viewModel().uiState.value
        assertEquals(listOf(wakes), kept.facts)
        assertNull(kept.error)
    }

    @Test
    fun `adding puts the new fact first, and a blank text is never sent`() {
        coEvery { ai.addMemory("Pays rent on the 1st") } returns Result.success(MemoryFact("f9", "Pays rent on the 1st", "OTHER"))
        val vm = viewModel()

        vm.add("   ")
        vm.add("  Pays rent on the 1st ")

        assertEquals("f9", vm.uiState.value.facts.first().id)
        coVerify(exactly = 1) { ai.addMemory(any()) }
        coVerify(exactly = 1) { ai.addMemory("Pays rent on the 1st") }
    }

    @Test
    fun `a refused fact shows the server's reason and changes nothing`() {
        coEvery { ai.addMemory(any()) } returns Result.failure(AiException("I do not keep passwords, keys, tokens or card and ID numbers."))
        val vm = viewModel()

        vm.add("my password is x")

        assertEquals("I do not keep passwords, keys, tokens or card and ID numbers.", vm.uiState.value.message)
        assertEquals(listOf(wakes, tea), vm.uiState.value.facts)
        vm.dismissMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `editing replaces the text and closes the editor, and a refusal keeps both`() {
        coEvery { ai.updateMemory("f1", "Wakes at 6") } returns Result.success(wakes.copy(text = "Wakes at 6"))
        coEvery { ai.updateMemory("f2", "the password is x") } returns Result.failure(AiException("I do not keep passwords, keys, tokens or card and ID numbers."))
        val vm = viewModel()

        vm.startEdit("f1")
        assertEquals("f1", vm.uiState.value.editing)
        vm.saveEdit("f1", "Wakes at 6")
        assertEquals("Wakes at 6", vm.uiState.value.facts.first { it.id == "f1" }.text)
        assertNull(vm.uiState.value.editing)

        vm.startEdit("f2")
        vm.saveEdit("f2", "the password is x")
        assertEquals("Likes tea", vm.uiState.value.facts.first { it.id == "f2" }.text)
        assertEquals("f2", vm.uiState.value.editing)
        assertEquals("I do not keep passwords, keys, tokens or card and ID numbers.", vm.uiState.value.message)

        vm.cancelEdit()
        assertNull(vm.uiState.value.editing)
    }

    @Test
    fun `deleting removes the fact, and a failure keeps it and says so`() {
        coEvery { ai.deleteMemory("f1") } returns Result.success(Unit)
        coEvery { ai.deleteMemory("f2") } returns Result.failure(AiException("Can't reach the server."))
        val vm = viewModel()

        vm.delete("f1")
        assertEquals(listOf(tea), vm.uiState.value.facts)

        vm.delete("f2")
        assertEquals(listOf(tea), vm.uiState.value.facts)
        assertEquals("Couldn't delete that. Try again.", vm.uiState.value.message)
    }

    @Test
    fun `deleting a fact that is already gone just removes it from the list`() {
        coEvery { ai.deleteMemory("f1") } returns Result.failure(AiException("That is not there any more.", notFound = true))
        val vm = viewModel()

        vm.delete("f1")

        assertEquals(listOf(tea), vm.uiState.value.facts)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `forget everything asks first, clears on confirm and keeps everything on cancel`() {
        coEvery { ai.deleteAllMemories() } returns Result.success(Unit)
        val vm = viewModel()

        vm.askForgetAll()
        assertTrue(vm.uiState.value.confirmingForgetAll)
        coVerify(exactly = 0) { ai.deleteAllMemories() }
        vm.cancelForgetAll()
        assertFalse(vm.uiState.value.confirmingForgetAll)
        assertEquals(2, vm.uiState.value.facts.size)

        vm.askForgetAll()
        vm.confirmForgetAll()

        assertTrue(vm.uiState.value.facts.isEmpty())
        assertFalse(vm.uiState.value.confirmingForgetAll)
        coVerify(exactly = 1) { ai.deleteAllMemories() }
    }
}
