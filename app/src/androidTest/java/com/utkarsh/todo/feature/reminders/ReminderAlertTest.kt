package com.utkarsh.todo.feature.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.reminders.platform.ReminderAlarmActivity
import com.utkarsh.todo.feature.reminders.platform.ReminderNotifications
import com.utkarsh.todo.feature.reminders.ui.ReminderAlertScreen
import com.utkarsh.todo.feature.reminders.ui.ReminderAlertUi
import com.utkarsh.todo.feature.reminders.ui.snoozeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The full-screen alert that covers the display when a reminder rings. */
class ReminderAlertTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val ui = ReminderAlertUi("Dentist appointment", "3:00 PM", "TUE 10 NOV", "Due now")

    private fun show(state: ReminderAlertUi = ui) {
        events.clear()
        compose.setContent {
            PaperTheme {
                ReminderAlertScreen(state, onSnooze = { events += "snooze:$it" }, onDismiss = { events += "dismiss" }, onOpen = { events += "open" })
            }
        }
    }

    @Test
    fun shows_what_is_ringing_and_when() {
        show()
        compose.onNodeWithTag("alert-title").assertIsDisplayed()
        compose.onNodeWithText("Dentist appointment").assertIsDisplayed()
        compose.onNodeWithText("3:00 PM").assertIsDisplayed()
        compose.onNodeWithText("TUE 10 NOV").assertIsDisplayed()
        compose.onNodeWithText("Due now").assertIsDisplayed()
        compose.onNodeWithText("REMINDER · NOW").assertIsDisplayed()
    }

    @Test
    fun offers_done_snooze_10_min_and_a_few_other_snoozes() {
        show()
        compose.onNodeWithTag("alert-dismiss").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Snooze 10 min").assertIsDisplayed()
        listOf("5 min", "30 min", "1 hr").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun done_says_nice_with_undo_and_only_happens_after_a_moment() {
        compose.mainClock.autoAdvance = false
        show()
        compose.onNodeWithTag("alert-dismiss").performClick()
        compose.onNodeWithText("Nice. One less.").assertIsDisplayed()
        compose.onNodeWithTag("alert-undo").assertIsDisplayed()
        assertEquals(emptyList<String>(), events) // not yet

        compose.mainClock.advanceTimeBy(3_500)
        compose.waitForIdle()
        assertEquals(listOf("dismiss"), events)
    }

    @Test
    fun snooze_says_when_it_is_back_and_undo_cancels_it() {
        compose.mainClock.autoAdvance = false
        show()
        compose.onNodeWithTag("alert-snooze-10").performClick()
        compose.onNodeWithText("Back in 10 min").assertIsDisplayed()
        compose.onNodeWithTag("alert-undo").performClick()
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertEquals(emptyList<String>(), events) // nothing happened, the alert is still there
        compose.onNodeWithTag("alert-dismiss").assertIsDisplayed()
    }

    @Test
    fun each_snooze_button_reports_its_minutes_after_the_pause() {
        compose.mainClock.autoAdvance = false
        show()
        compose.onNodeWithTag("alert-snooze-30").performClick()
        compose.mainClock.advanceTimeBy(3_500)
        compose.waitForIdle()
        assertEquals(listOf("snooze:30"), events)
    }

    @Test
    fun open_opens_straight_away() {
        show()
        compose.onNodeWithTag("alert-open").performClick()
        assertEquals(listOf("open"), events)
    }

    @Test
    fun the_open_link_names_the_task_when_there_is_one() {
        show(ui.copy(canOpenTask = true))
        compose.onNodeWithText("Open task").assertIsDisplayed()
    }

    @Test
    fun snooze_labels_read_naturally() {
        assertEquals(listOf("5 min", "10 min", "30 min", "1 hr", "2 hr"), listOf(5, 10, 30, 60, 120).map(::snoozeLabel))
    }

    @Test
    fun back_on_the_alert_activity_stops_the_ring_and_closes_it() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = "alert-activity-1"
        manager.cancelAll()
        ReminderNotifications(context).show(id, "Dentist appointment")
        val intent = ReminderAlarmActivity.intent(context, id, "Dentist appointment", System.currentTimeMillis(), null, null)

        ActivityScenario.launch<ReminderAlarmActivity>(intent).use { scenario ->
            val deadline = System.currentTimeMillis() + 5_000
            while (manager.activeNotifications.none { it.id == id.hashCode() } && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertTrue("it is ringing before the person acts", manager.activeNotifications.any { it.id == id.hashCode() })

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } // Back = dismiss

            val end = System.currentTimeMillis() + 5_000
            while (scenario.state != Lifecycle.State.DESTROYED && System.currentTimeMillis() < end) Thread.sleep(100)
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            assertTrue("the ring is stopped", manager.activeNotifications.none { it.id == id.hashCode() })
        }
    }
}
