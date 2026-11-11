package com.utkarsh.todo.feature.customize

import app.cash.turbine.test
import com.utkarsh.todo.feature.customize.domain.AppSettings
import com.utkarsh.todo.feature.customize.domain.PaperBackground
import com.utkarsh.todo.feature.customize.domain.RowDensity
import com.utkarsh.todo.feature.customize.domain.TodayBlock
import com.utkarsh.todo.feature.customize.ui.CustomizeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomizeViewModelTest {
    private val repo = FakeSettingsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_state_starts_unloaded_then_shows_the_saved_settings() = runTest {
        val saved = AppSettings(density = RowDensity.COMPACT)
        CustomizeViewModel(FakeSettingsRepository(saved)).uiState.test {
            val first = awaitItem()
            val loaded = if (first.isLoaded) first else awaitItem()
            assertTrue(loaded.isLoaded)
            assertEquals(saved, loaded.settings)
        }
    }

    @Test
    fun switching_a_block_changes_what_is_visible_and_saves_at_once() = runTest {
        val vm = CustomizeViewModel(repo)
        vm.uiState.test {
            skipItems(1)
            vm.setVisible(TodayBlock.SCRATCHPAD, true)
            assertTrue(awaitItem().settings.isVisible(TodayBlock.SCRATCHPAD))
            vm.setVisible(TodayBlock.PINNED, false)
            assertFalse(awaitItem().settings.isVisible(TodayBlock.PINNED))
        }
        assertEquals(setOf(TodayBlock.PINNED), repo.current.hiddenBlocks)
    }

    @Test
    fun moving_a_block_reorders_the_list() = runTest {
        val vm = CustomizeViewModel(repo)
        vm.uiState.test {
            skipItems(1)
            vm.move(TodayBlock.TASKS, -1)
            assertEquals(
                listOf(TodayBlock.TASKS, TodayBlock.PINNED, TodayBlock.SCRATCHPAD),
                awaitItem().settings.blockOrder,
            )
        }
    }

    @Test
    fun moving_off_the_end_saves_nothing_new() = runTest {
        val vm = CustomizeViewModel(repo)
        vm.uiState.test {
            skipItems(1)
            vm.move(TodayBlock.PINNED, -1)
            expectNoEvents() // the state is identical, so nothing is emitted
        }
        assertEquals(AppSettings(), repo.current)
    }

    @Test
    fun background_and_density_are_chosen_independently() = runTest {
        val vm = CustomizeViewModel(repo)
        vm.uiState.test {
            skipItems(1)
            vm.setBackground(PaperBackground.LINES)
            assertEquals(PaperBackground.LINES, awaitItem().settings.background)
            vm.setDensity(RowDensity.COMPACT)
            val s = awaitItem().settings
            assertEquals(RowDensity.COMPACT, s.density)
            assertEquals(PaperBackground.LINES, s.background)
        }
    }

    @Test
    fun reset_restores_every_default_including_order_and_hidden_blocks() = runTest {
        val vm = CustomizeViewModel(
            FakeSettingsRepository(
                AppSettings().withVisible(TodayBlock.PINNED, false).moved(TodayBlock.TASKS, -1)
                    .copy(background = PaperBackground.DOTS, density = RowDensity.COMPACT),
            ).also { vmRepo -> assertTrue(vmRepo.current != AppSettings()) },
        )
        vm.uiState.test {
            skipItems(1)
            vm.resetToDefaults()
            assertEquals(AppSettings(), awaitItem().settings)
        }
    }
}
