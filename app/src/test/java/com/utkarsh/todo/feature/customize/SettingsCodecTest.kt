package com.utkarsh.todo.feature.customize

import com.utkarsh.todo.feature.customize.data.SettingsCodec
import com.utkarsh.todo.feature.customize.domain.PaperBackground
import com.utkarsh.todo.feature.customize.domain.TodayBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCodecTest {
    private val default = TodayBlock.entries.toList()

    @Test
    fun an_order_round_trips() {
        val order = listOf(TodayBlock.SCRATCHPAD, TodayBlock.PINNED, TodayBlock.TASKS)
        assertEquals(order, SettingsCodec.decodeOrder(SettingsCodec.encodeOrder(order)))
    }

    @Test
    fun a_missing_or_empty_order_gives_the_default() {
        assertEquals(default, SettingsCodec.decodeOrder(null))
        assertEquals(default, SettingsCodec.decodeOrder(""))
        assertEquals(default, SettingsCodec.decodeOrder("NOPE,WHAT"))
    }

    @Test
    fun a_partial_order_keeps_what_it_has_and_appends_the_rest() {
        assertEquals(
            listOf(TodayBlock.SCRATCHPAD, TodayBlock.PINNED, TodayBlock.TASKS),
            SettingsCodec.decodeOrder("SCRATCHPAD,PINNED"),
        )
    }

    @Test
    fun unknown_names_repeats_and_spaces_are_cleaned_up() {
        assertEquals(
            listOf(TodayBlock.TASKS, TodayBlock.PINNED, TodayBlock.SCRATCHPAD),
            SettingsCodec.decodeOrder(" TASKS ,GONE,TASKS,PINNED"),
        )
    }

    @Test
    fun a_saved_order_that_still_mentions_the_removed_habits_block_keeps_the_rest() {
        assertEquals(
            listOf(TodayBlock.TASKS, TodayBlock.PINNED, TodayBlock.SCRATCHPAD),
            SettingsCodec.decodeOrder("HABITS,TASKS,PINNED,SCRATCHPAD"),
        )
        assertEquals(setOf(TodayBlock.PINNED), SettingsCodec.decodeHidden(setOf("HABITS", "PINNED")))
    }

    @Test
    fun hidden_blocks_never_saved_use_the_design_default_but_an_empty_set_means_none() {
        assertEquals(setOf(TodayBlock.SCRATCHPAD), SettingsCodec.decodeHidden(null))
        assertEquals(emptySet<TodayBlock>(), SettingsCodec.decodeHidden(emptySet()))
    }

    @Test
    fun hidden_blocks_round_trip_and_ignore_unknown_names() {
        val hidden = setOf(TodayBlock.SCRATCHPAD, TodayBlock.PINNED)
        assertEquals(hidden, SettingsCodec.decodeHidden(SettingsCodec.encodeHidden(hidden)))
        assertEquals(setOf(TodayBlock.TASKS), SettingsCodec.decodeHidden(setOf("TASKS", "REMOVED_IN_V2")))
    }

    @Test
    fun enums_fall_back_to_the_default_for_unknown_values() {
        val all = PaperBackground.entries.toTypedArray()
        assertEquals(PaperBackground.LINES, SettingsCodec.decodeEnum(all, "LINES", PaperBackground.PLAIN))
        assertEquals(PaperBackground.PLAIN, SettingsCodec.decodeEnum(all, "DIAGONAL", PaperBackground.PLAIN))
        assertEquals(PaperBackground.PLAIN, SettingsCodec.decodeEnum(all, null, PaperBackground.PLAIN))
    }
}
