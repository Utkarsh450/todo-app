package com.utkarsh.todo.core.offline

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineViewModelTest {
    private val monitor = OfflineMonitor()
    private val health = mockk<HealthApi>()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a failed check leaves us offline`() {
        monitor.markOffline()
        coEvery { health.health() } throws RuntimeException("down")
        val vm = OfflineViewModel(monitor, health)
        vm.recheck()
        assertTrue(vm.offline.value)
    }

    @Test
    fun `a successful check brings us back online`() {
        monitor.markOffline()
        coEvery { health.health() } returns Unit
        val vm = OfflineViewModel(monitor, health)
        vm.recheck()
        coVerify { health.health() }
        assertFalse(vm.offline.value)
    }

    @Test
    fun `the monitor starts online and only announces real reconnections`() {
        val seen = mutableListOf<Unit>()
        val job = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()).also { scope ->
            scope.launchCollect(monitor.reconnected) { seen += it }
        }
        monitor.markOnline() // was never offline: nothing to announce
        assertTrue(seen.isEmpty())
        monitor.markOffline()
        monitor.markOffline()
        monitor.markOnline()
        monitor.markOnline()
        assertTrue(seen.size == 1)
        job.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun <T> kotlinx.coroutines.CoroutineScope.launchCollect(flow: kotlinx.coroutines.flow.Flow<T>, action: (T) -> Unit) {
        flow.onEach { action(it) }.launchIn(this)
    }
}
