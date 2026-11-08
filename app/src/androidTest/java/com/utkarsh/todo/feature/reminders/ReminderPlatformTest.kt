package com.utkarsh.todo.feature.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.platform.AlarmReminderScheduler
import com.utkarsh.todo.feature.reminders.platform.ReminderActionReceiver
import com.utkarsh.todo.feature.reminders.platform.ReminderNotifications
import com.utkarsh.todo.feature.reminders.platform.ReminderReceiver
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Runs on the emulator against the real AlarmManager and NotificationManager. */
class ReminderPlatformTest {
    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val scheduler = AlarmReminderScheduler(context)
    private val id = "instrumented-reminder-1"

    private fun reminder(at: Instant = Instant.now().plusSeconds(3600)) = Reminder(
        id = id, taskId = null, title = "Drink water", remindAt = at, repeatRule = null,
        locationTrigger = null, enabled = true, snoozedUntil = null,
    )

    private fun existingAlarm(): PendingIntent? = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE).putExtra(ReminderReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    @Before
    fun setUp() {
        notifications.cancelAll()
        scheduler.cancel(id)
    }

    @After
    fun tearDown() {
        scheduler.cancel(id)
        notifications.cancelAll()
    }

    @Test
    fun schedule_registers_an_alarm_and_cancel_removes_it() {
        assertNull(existingAlarm())

        scheduler.schedule(reminder())
        assertNotNull(existingAlarm())

        scheduler.cancel(id)
        assertNull(existingAlarm())
    }

    /** Notifications are posted asynchronously by the system, so poll for the expected title. */
    private fun awaitTitle(notificationId: String): String? {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            notifications.activeNotifications.firstOrNull { it.id == notificationId.hashCode() }
                ?.let { return it.notification.extras.getString("android.title") }
            Thread.sleep(100)
        }
        return null
    }

    @Test
    fun notification_shows_the_reminder_title() {
        val showId = "$id-show"
        ReminderNotifications(context).show(showId, "Drink water")

        assertEquals("Drink water", awaitTitle(showId))
    }

    @Test
    fun firing_the_alarm_broadcast_posts_a_notification() {
        val fireId = "$id-fire"
        context.sendBroadcast(
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_FIRE)
                .putExtra(ReminderReceiver.EXTRA_ID, fireId)
                .putExtra(ReminderReceiver.EXTRA_TITLE, "Standup"),
        )

        assertEquals("Standup", awaitTitle(fireId))
    }

    private fun posted(notificationId: String) = notifications.activeNotifications.firstOrNull { it.id == notificationId.hashCode() }

    private fun awaitGone(notificationId: String): Boolean {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (posted(notificationId) == null) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun await(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return false
    }

    @Test
    fun the_ringing_notification_is_an_insistent_alarm_with_a_full_screen_alert_and_two_buttons() {
        val ringId = "$id-ring"
        ReminderNotifications(context).show(ringId, "Dentist", Instant.now().toEpochMilli(), taskId = null, repeatRule = null)
        awaitTitle(ringId)

        val n = posted(ringId)!!.notification
        assertNotNull("opens the alert full screen", n.fullScreenIntent)
        assertEquals(listOf("Snooze 10 min", "Dismiss"), n.actions.map { it.title.toString() })
        assertEquals(Notification.FLAG_INSISTENT, n.flags and Notification.FLAG_INSISTENT)
        assertEquals(ReminderNotifications.CHANNEL_ID, n.channelId)

        val channel = notifications.getNotificationChannel(ReminderNotifications.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNotNull("rings with a sound", channel.sound)
        assertEquals(android.media.AudioAttributes.USAGE_ALARM, channel.audioAttributes.usage)
        assertTrue(channel.shouldVibrate())
    }

    @Test
    fun the_dismiss_button_stops_the_ring() {
        val ringId = "$id-dismiss"
        ReminderNotifications(context).show(ringId, "Dentist")
        awaitTitle(ringId)

        context.sendBroadcast(
            Intent(context, ReminderActionReceiver::class.java).setAction(ReminderActionReceiver.ACTION_DISMISS).putExtra(ReminderReceiver.EXTRA_ID, ringId),
        )

        assertTrue(awaitGone(ringId))
    }

    @Test
    fun the_snooze_button_stops_the_ring_and_sets_the_alarm_again_even_without_a_server() {
        val snoozeId = id // the one existingAlarm() looks for
        ReminderNotifications(context).show(snoozeId, "Dentist", Instant.now().toEpochMilli())
        awaitTitle(snoozeId)
        assertNull(existingAlarm())

        context.sendBroadcast(
            Intent(context, ReminderActionReceiver::class.java).setAction(ReminderActionReceiver.ACTION_SNOOZE)
                .putExtra(ReminderReceiver.EXTRA_ID, snoozeId)
                .putExtra(ReminderReceiver.EXTRA_TITLE, "Dentist")
                .putExtra(ReminderReceiver.EXTRA_REMIND_AT, Instant.now().toEpochMilli())
                .putExtra(ReminderActionReceiver.EXTRA_MINUTES, 10),
        )

        assertTrue(awaitGone(snoozeId))
        assertTrue("alarm is set again", await { existingAlarm() != null })
    }
}
