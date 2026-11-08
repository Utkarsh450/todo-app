package com.utkarsh.todo.feature.customize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.customize.domain.TodayBlock
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.today.domain.TaskItem
import com.utkarsh.todo.feature.today.domain.TodayView
import com.utkarsh.todo.feature.today.ui.TodayScreen
import com.utkarsh.todo.feature.today.ui.TodayUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Today draws exactly the blocks it is told to, in the order it is told. */
class TodayBlocksTest {
    @get:Rule
    val compose = createComposeRule()

    private val pinned = TaskItem("p", "Ship the redesign", "9:00 AM", false, Priority.HIGH, true, null, null, 1, 3)
    private val plain = TaskItem("t", "Water the plants", "10:00 AM", false, Priority.NORMAL, false, null, null, 0, 0)
    private val state = TodayUiState(isLoading = false, caption = "SAT 7 NOV · W45", items = listOf(pinned, plain), view = TodayView.BLOCKS)
    private var opened = 0

    private var askedGrok = 0
    private var openedDump = 0

    private fun show(blocks: List<TodayBlock>?, withCustomize: Boolean = false, withAssistant: Boolean = false, withBrainDump: Boolean = false) {
        compose.setContent {
            PaperTheme {
                TodayScreen(
                    state = state, onViewChange = {}, onToggle = {}, onOpen = {}, onMoveToTomorrow = {}, onArchive = {},
                    onUndo = {}, onDismissUndo = {}, onRetry = {},
                    habitsContent = { Box(Modifier.fillMaxWidth().height(40.dp).testTag("habits-slot")) },
                    noteContent = { Box(Modifier.fillMaxWidth().height(40.dp).testTag("note-slot")) },
                    blocks = blocks ?: TodayBlock.entries,
                    onCustomize = if (withCustomize) ({ opened++ }) else null,
                    onAssistant = if (withAssistant) ({ askedGrok++ }) else null,
                    onBrainDump = if (withBrainDump) ({ openedDump++ }) else null,
                )
            }
        }
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun by_default_every_block_is_shown() {
        show(null)
        compose.onNodeWithText("PINNED").assertIsDisplayed()
        compose.onNodeWithText("TODAY'S LIST").assertIsDisplayed()
        compose.onNodeWithTag("habits-slot").assertIsDisplayed()
        compose.onNodeWithTag("note-slot").assertIsDisplayed()
    }

    @Test
    fun a_block_left_out_is_not_drawn() {
        show(listOf(TodayBlock.TASKS, TodayBlock.HABITS)) // no pinned, no scratchpad
        compose.onNodeWithText("PINNED").assertDoesNotExist()
        compose.onNodeWithTag("note-slot").assertDoesNotExist()
        compose.onNodeWithText("TODAY'S LIST").assertIsDisplayed()
        compose.onNodeWithTag("habits-slot").assertIsDisplayed()
    }

    @Test
    fun blocks_appear_in_the_given_order() {
        show(listOf(TodayBlock.HABITS, TodayBlock.TASKS))
        assertTrue(top("habits-slot") < compose.onNodeWithText("TODAY'S LIST").fetchSemanticsNode().boundsInRoot.top)
    }

    @Test
    fun the_tasks_can_sit_below_the_habits_or_above_them() {
        show(listOf(TodayBlock.TASKS, TodayBlock.HABITS))
        assertTrue(compose.onNodeWithText("TODAY'S LIST").fetchSemanticsNode().boundsInRoot.top < top("habits-slot"))
    }

    @Test
    fun with_no_blocks_the_screen_still_shows_its_header() {
        show(emptyList())
        compose.onNodeWithTag("today-title").assertIsDisplayed()
        compose.onNodeWithText("TODAY'S LIST").assertDoesNotExist()
    }

    @Test
    fun the_customize_button_appears_only_when_it_has_somewhere_to_go() {
        show(null, withCustomize = false)
        compose.onNodeWithTag("open-customize").assertDoesNotExist()
    }

    @Test
    fun tapping_the_customize_button_opens_customize() {
        show(null, withCustomize = true)
        compose.onNodeWithTag("open-customize").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun the_assistant_button_appears_only_when_it_has_somewhere_to_go() {
        show(null, withAssistant = false)
        compose.onNodeWithTag("open-assistant").assertDoesNotExist()
    }

    @Test
    fun tapping_the_assistant_button_opens_the_assistant() {
        show(null, withAssistant = true)
        compose.onNodeWithTag("open-assistant").performClick()
        assertEquals(1, askedGrok)
    }

    @Test
    fun the_brain_dump_button_appears_only_when_it_has_somewhere_to_go() {
        show(null, withBrainDump = false)
        compose.onNodeWithTag("open-braindump").assertDoesNotExist()
    }

    @Test
    fun tapping_the_brain_dump_button_opens_it() {
        show(null, withBrainDump = true)
        compose.onNodeWithTag("open-braindump").performClick()
        assertEquals(1, openedDump)
    }
}
