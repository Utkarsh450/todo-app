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
        compose.onNodeWithText("DUE NOW").assertIsDisplayed()
        compose.onNodeWithText("REMINDER").assertIsDisplayed()
    }

    @Test
    fun offers_four_snooze_choices_and_a_big_dismiss_button() {
        show()
        listOf("5 min", "10 min", "30 min", "1 hr").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithTag("alert-dismiss").assertIsDisplayed()
    }

    @Test
    fun each_button_does_one_thing() {
        show()
        compose.onNodeWithTag("alert-snooze-30").performClick()
        compose.onNodeWithTag("alert-snooze-5").performClick()
        compose.onNodeWithTag("alert-dismiss").performClick()
        compose.onNodeWithTag("alert-open").performClick()
        assertEquals(listOf("snooze:30", "snooze:5", "dismiss", "open"), events)
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
