package com.utkarsh.todo.feature.reminders

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.reminders.ui.HeroUi
import com.utkarsh.todo.feature.reminders.ui.ReminderRow
import com.utkarsh.todo.feature.reminders.ui.ReminderSection
import com.utkarsh.todo.feature.reminders.ui.DueSoonScreen
import com.utkarsh.todo.feature.reminders.ui.RemindersUiState
import com.utkarsh.todo.feature.reminders.ui.SectionKind
import com.utkarsh.todo.feature.reminders.ui.SnoozeOption
import com.utkarsh.todo.feature.today.domain.UndoPrompt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DueSoonScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val water = ReminderRow("r1", "2:00", "PM", "Drink water", null, "↻ Every 2 Hours", null, enabled = true)
    private val groceries = ReminderRow("r2", "5:30", "PM", "Buy groceries", null, null, "⌖ When I leave the office", enabled = false)
    private val bill = ReminderRow("r0", "8:00", "PM", "Pay electricity bill", "Yesterday", null, null, enabled = true)

    private fun state(
        hero: HeroUi? = HeroUi("r1", "Drink water", "In 19 min · 2:00 PM", snoozed = false),
        undo: UndoPrompt? = null,
    ) = RemindersUiState(
        isLoading = false, hero = hero, undo = undo, activeCount = 2,
        sections = listOf(
            ReminderSection(SectionKind.OVERDUE, "Overdue", listOf(bill)),
            ReminderSection(SectionKind.TODAY, "Today", listOf(water, groceries)),
        ),
    )

    private fun show(state: RemindersUiState) {
        events.clear()
        compose.setContent {
            PaperTheme {
                DueSoonScreen(
                    state = state,
                    onToggle = { id, on -> events += "toggle:$id:$on" },
                    onSnoozeRow = { events += "snooze:$it" },
                    onDelete = { events += "delete:$it" },
                    onSnoozeHero = { events += "hero:${it.label}" },
                    onUndo = { events += "undo" },
                    onDismissUndo = {},
                    onRetry = { events += "retry" },
                )
            }
        }
    }

    @Test
    fun shows_hero_sections_and_rows() {
        show(state())

        compose.onNodeWithTag("due-soon-title").assertIsDisplayed()
        compose.onNodeWithText("Due soon").assertIsDisplayed()
        compose.onNodeWithText("2 ACTIVE").assertIsDisplayed()
        compose.onNodeWithTag("next-up").assertIsDisplayed()
        compose.onNodeWithText("NEXT UP").assertIsDisplayed()
        compose.onNodeWithText("In 19 min · 2:00 PM").assertIsDisplayed()
        compose.onNodeWithText("OVERDUE").assertIsDisplayed()
        compose.onNodeWithText("Pay electricity bill").assertIsDisplayed()
        compose.onNodeWithText("↻ Every 2 Hours").assertIsDisplayed()
        compose.onNodeWithText("⌖ When I leave the office").assertIsDisplayed()
    }

    @Test
    fun switch_reflects_state_and_reports_changes() {
        show(state())

        compose.onNodeWithContentDescription("Reminder Drink water").assertIsOn().performClick()
        compose.onNodeWithContentDescription("Reminder Buy groceries").assertIsOff()

        assertEquals(listOf("toggle:r1:false"), events)
    }

    @Test
    fun hero_snooze_chips_report_the_option() {
        show(state())

        SnoozeOption.entries.forEach { compose.onNode(androidx.compose.ui.test.hasText(it.label) and androidx.compose.ui.test.hasClickAction()).performClick() }

        assertEquals(listOf("hero:10 min", "hero:1 hour", "hero:Tomorrow"), events)
    }

    @Test
    fun snoozed_hero_uses_snoozed_tag() {
        show(state(hero = HeroUi("r1", "Drink water", "In 1 h · 3:00 PM", snoozed = true)))
        compose.onNodeWithText("SNOOZED").assertIsDisplayed()
    }

    @Test
    fun swipes_snooze_and_delete() {
        show(state())

        compose.onNodeWithTag("reminder-r1").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag("reminder-r2").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(listOf("snooze:r1", "delete:r2"), events)
    }

    @Test
    fun undo_works_and_there_is_no_new_reminder_button() {
        show(state(undo = UndoPrompt("Reminder deleted")))

        compose.onNodeWithTag("add-reminder").assertDoesNotExist() // creating happens only through the + button
        compose.onNodeWithText("Undo").performClick()

        assertEquals(listOf("undo"), events)
    }

    @Test
    fun empty_state_is_friendly() {
        show(RemindersUiState(isLoading = false))
        compose.onNodeWithText("Nothing is due soon.").assertIsDisplayed()
    }

    @Test
    fun error_state_offers_retry() {
        show(RemindersUiState(isLoading = false, error = "offline"))
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf("retry"), events)
    }
}
