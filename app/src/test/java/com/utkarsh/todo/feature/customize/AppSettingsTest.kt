package com.utkarsh.todo.feature.customize

import com.utkarsh.todo.feature.customize.domain.AppSettings
import com.utkarsh.todo.feature.customize.domain.PaperBackground
import com.utkarsh.todo.feature.customize.domain.RowDensity
import com.utkarsh.todo.feature.customize.domain.TodayBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun defaults_follow_the_design_scratchpad_off_cozy_plain() {
        val s = AppSettings()
        assertEquals(listOf(TodayBlock.PINNED, TodayBlock.TASKS, TodayBlock.SCRATCHPAD), s.blockOrder)
        assertEquals(setOf(TodayBlock.SCRATCHPAD), s.hiddenBlocks)
        assertEquals(PaperBackground.PLAIN, s.background)
        assertEquals(RowDensity.COZY, s.density)
        assertEquals(listOf(TodayBlock.PINNED, TodayBlock.TASKS), s.visibleBlocks)
    }

    @Test
    fun visible_blocks_keep_the_chosen_order_and_skip_hidden_ones() {
        val s = AppSettings(
            blockOrder = listOf(TodayBlock.SCRATCHPAD, TodayBlock.TASKS, TodayBlock.PINNED),
            hiddenBlocks = setOf(TodayBlock.TASKS),
        )
        assertEquals(listOf(TodayBlock.SCRATCHPAD, TodayBlock.PINNED), s.visibleBlocks)
    }

    @Test
    fun switching_a_block_off_and_on_again_restores_its_place() {
        val s = AppSettings().withVisible(TodayBlock.TASKS, false)
        assertFalse(s.isVisible(TodayBlock.TASKS))
        assertEquals(listOf(TodayBlock.PINNED), s.visibleBlocks)
        val back = s.withVisible(TodayBlock.TASKS, true)
        assertTrue(back.isVisible(TodayBlock.TASKS))
        assertEquals(listOf(TodayBlock.PINNED, TodayBlock.TASKS), back.visibleBlocks)
    }

    @Test
    fun showing_a_hidden_block_and_hiding_a_hidden_block_are_both_safe() {
        assertEquals(AppSettings(), AppSettings().withVisible(TodayBlock.SCRATCHPAD, false))
        assertTrue(AppSettings().withVisible(TodayBlock.SCRATCHPAD, true).hiddenBlocks.isEmpty())
    }

    @Test
    fun moving_swaps_with_the_neighbour() {
        val up = AppSettings().moved(TodayBlock.TASKS, -1)
        assertEquals(listOf(TodayBlock.TASKS, TodayBlock.PINNED, TodayBlock.SCRATCHPAD), up.blockOrder)
        val down = AppSettings().moved(TodayBlock.PINNED, +1)
        assertEquals(listOf(TodayBlock.TASKS, TodayBlock.PINNED, TodayBlock.SCRATCHPAD), down.blockOrder)
    }

    @Test
    fun moving_past_either_end_changes_nothing() {
        val s = AppSettings()
        assertSame(s, s.moved(TodayBlock.PINNED, -1))
        assertSame(s, s.moved(TodayBlock.SCRATCHPAD, +1))
        assertFalse(s.canMoveUp(TodayBlock.PINNED))
        assertTrue(s.canMoveDown(TodayBlock.PINNED))
        assertTrue(s.canMoveUp(TodayBlock.SCRATCHPAD))
        assertFalse(s.canMoveDown(TodayBlock.SCRATCHPAD))
    }

    @Test
    fun moving_by_more_than_one_or_zero_is_ignored() {
        val s = AppSettings()
        assertSame(s, s.moved(TodayBlock.TASKS, 0))
        assertSame(s, s.moved(TodayBlock.TASKS, 2))
        assertSame(s, s.moved(TodayBlock.TASKS, -2))
    }

    @Test
    fun hidden_blocks_move_like_any_other() {
        val s = AppSettings().moved(TodayBlock.SCRATCHPAD, -1)
        assertEquals(listOf(TodayBlock.PINNED, TodayBlock.SCRATCHPAD, TodayBlock.TASKS), s.blockOrder)
        assertEquals(setOf(TodayBlock.SCRATCHPAD), s.hiddenBlocks)
    }

    @Test
    fun an_order_that_misses_or_repeats_a_block_is_rejected() {
        assertThrows(IllegalArgumentException::class.java) { AppSettings(blockOrder = listOf(TodayBlock.PINNED)) }
        assertThrows(IllegalArgumentException::class.java) {
            AppSettings(blockOrder = listOf(TodayBlock.PINNED, TodayBlock.PINNED, TodayBlock.TASKS))
        }
    }
}
