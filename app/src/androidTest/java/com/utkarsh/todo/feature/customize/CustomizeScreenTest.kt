package com.utkarsh.todo.feature.customize

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.customize.domain.AppSettings
import com.utkarsh.todo.feature.customize.domain.TodayBlock
import com.utkarsh.todo.feature.customize.ui.CustomizeScreen
import com.utkarsh.todo.feature.customize.ui.CustomizeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CustomizeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(settings: AppSettings = AppSettings()) {
        events.clear()
        compose.setContent {
            PaperTheme {
                CustomizeScreen(
                    state = CustomizeUiState(settings, isLoaded = true),
                    onBack = { events += "back" },
                    onSetVisible = { block, visible -> events += "visible:${block.name}:$visible" },
                    onMove = { block, delta -> events += "move:${block.name}:$delta" },
                    onBackground = { events += "background:${it.name}" },
                    onDensity = { events += "density:${it.name}" },
                    onReset = { events += "reset" },
                )
            }
        }
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun shows_the_title_and_every_block_with_its_hint() {
        show()
        compose.onNodeWithTag("customize-title").assertIsDisplayed()
        TodayBlock.entries.forEach { block ->
            compose.onNodeWithTag("block-row-${block.name}").assertIsDisplayed()
            compose.onNodeWithText(block.label).assertIsDisplayed()
            compose.onNodeWithText(block.hint).assertIsDisplayed()
        }
    }

    @Test
    fun rows_follow_the_saved_order() {
        show(AppSettings(blockOrder = listOf(TodayBlock.HABITS, TodayBlock.PINNED, TodayBlock.SCRATCHPAD, TodayBlock.TASKS)))
        assertTrue(top("block-row-HABITS") < top("block-row-PINNED"))
        assertTrue(top("block-row-PINNED") < top("block-row-SCRATCHPAD"))
        assertTrue(top("block-row-SCRATCHPAD") < top("block-row-TASKS"))
    }

    @Test
    fun switches_show_what_is_visible_and_the_design_default_has_the_scratchpad_off() {
        show()
        compose.onNodeWithTag("block-switch-PINNED").assertIsOn()
        compose.onNodeWithTag("block-switch-TASKS").assertIsOn()
        compose.onNodeWithTag("block-switch-HABITS").assertIsOn()
        compose.onNodeWithTag("block-switch-SCRATCHPAD").assertIsOff()
    }

    @Test
    fun tapping_a_switch_reports_the_opposite_value() {
        show()
        compose.onNodeWithTag("block-switch-SCRATCHPAD").performClick()
        compose.onNodeWithTag("block-switch-PINNED").performClick()
        assertEquals(listOf("visible:SCRATCHPAD:true", "visible:PINNED:false"), events)
    }

    @Test
    fun the_arrows_report_moves() {
        show()
        compose.onNodeWithTag("move-down-PINNED").performClick()
        compose.onNodeWithTag("move-up-TASKS").performClick()
        assertEquals(listOf("move:PINNED:1", "move:TASKS:-1"), events)
    }

    @Test
    fun the_first_block_cannot_move_up_and_the_last_cannot_move_down() {
        show()
        compose.onNodeWithTag("move-up-PINNED").assertHasNoClickAction()
        compose.onNodeWithTag("move-down-SCRATCHPAD").assertHasNoClickAction()
        compose.onNodeWithTag("move-down-PINNED").assertHasClickAction()
        compose.onNodeWithTag("move-up-SCRATCHPAD").assertHasClickAction()
    }

    @Test
    fun paper_and_density_choices_are_reported() {
        show()
        compose.onNodeWithText("Lines").performScrollTo().performClick()
        compose.onNodeWithText("Dots").performScrollTo().performClick()
        compose.onNodeWithText("Compact").performScrollTo().performClick()
        assertEquals(listOf("background:LINES", "background:DOTS", "density:COMPACT"), events)
    }

    @Test
    fun reset_and_back_are_reported() {
        show()
        compose.onNodeWithTag("customize-reset").performScrollTo().performClick()
        compose.onNodeWithTag("customize-back").performClick()
        assertEquals(listOf("reset", "back"), events)
    }
}
