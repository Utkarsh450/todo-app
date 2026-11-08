package com.utkarsh.todo.feature.reminders

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.platform.AlarmReminderScheduler
import com.utkarsh.todo.feature.reminders.platform.ReminderNotifications
import com.utkarsh.todo.feature.reminders.platform.ReminderReceiver
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun notification_shows_the_reminder_title() {
        ReminderNotifications(context).show(id, "Drink water")

        val active = notifications.activeNotifications.single { it.id == id.hashCode() }
        assertEquals("Drink water", active.notification.extras.getString("android.title"))
    }

    @Test
    fun firing_the_alarm_broadcast_posts_a_notification() {
        context.sendBroadcast(
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_FIRE)
                .putExtra(ReminderReceiver.EXTRA_ID, id)
                .putExtra(ReminderReceiver.EXTRA_TITLE, "Standup"),
        )

        val deadline = System.currentTimeMillis() + 5_000
        while (notifications.activeNotifications.none { it.id == id.hashCode() } && System.currentTimeMillis() < deadline) Thread.sleep(100)

        val posted = notifications.activeNotifications.single { it.id == id.hashCode() }
        assertEquals("Standup", posted.notification.extras.getString("android.title"))
    }
}
