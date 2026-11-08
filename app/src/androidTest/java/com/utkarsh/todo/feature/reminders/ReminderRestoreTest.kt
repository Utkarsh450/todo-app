package com.utkarsh.todo.feature.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.reminders.data.RemindersApi
import com.utkarsh.todo.feature.reminders.data.RemindersRepositoryImpl
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.platform.AlarmReminderScheduler
import com.utkarsh.todo.feature.reminders.platform.FileReminderAlarmStore
import com.utkarsh.todo.feature.reminders.platform.ReminderReceiver
import com.utkarsh.todo.feature.reminders.platform.StoringReminderScheduler
import java.io.File
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.create

/** Real AlarmManager: alarms survive "a reboot" with no network, and repeats are counted from the scheduled time. */
class ReminderRestoreTest {
    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val id = "instrumented-restore-1"
    private val alarms = AlarmReminderScheduler(context)
    private lateinit var storeFile: File

    @Before
    fun setUp() {
        storeFile = File(context.cacheDir, "restore-test-${System.nanoTime()}.json")
        alarms.cancel(id)
    }

    @After
    fun tearDown() {
        alarms.cancel(id)
        storeFile.delete()
        // The broadcast test below goes through the app's own store; leave nothing behind.
        FileReminderAlarmStore(File(context.filesDir, "scheduled-reminders.json")).remove(id)
    }

    private fun reminder(at: Instant, repeat: String? = null) =
        Reminder(id, null, "Drink water", at, repeat, null, true, null)

    private fun existingAlarm(): PendingIntent? = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE).putExtra(ReminderReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    @Test
    fun scheduling_is_remembered_and_cancelling_forgets() {
        val store = FileReminderAlarmStore(storeFile)
        val scheduler = StoringReminderScheduler(alarms, store)

        scheduler.schedule(reminder(Instant.now().plusSeconds(3600)))
        assertNotNull(existingAlarm())
        assertEquals(listOf(id), store.all().map { it.id })

        scheduler.cancel(id)
        assertNull(existingAlarm())
        assertEquals(emptyList<String>(), store.all().map { it.id })
    }

    @Test
    fun alarms_come_back_after_a_reboot_even_with_no_network() = runBlocking {
        val store = FileReminderAlarmStore(storeFile)
        StoringReminderScheduler(alarms, store).schedule(reminder(Instant.now().plusSeconds(3600)))
        alarms.cancel(id) // a reboot wipes the OS alarms; the phone's own record stays
        assertNull(existingAlarm())

        // The backend is unreachable (nothing listens on port 1), and the alarm must still come back.
        val offline = RemindersRepositoryImpl(
            buildRetrofit("http://127.0.0.1:1/api/").create<RemindersApi>(),
            StoringReminderScheduler(alarms, store),
            Clock.systemUTC(),
            store,
        )
        offline.restoreAlarms().getOrThrow()

        assertNotNull(existingAlarm())
    }

    @Test
    fun a_repeat_is_counted_from_the_scheduled_time_not_from_when_the_alarm_fired() {
        // The app's own receiver and store handle this broadcast, exactly as when a real alarm goes off.
        val appStore = FileReminderAlarmStore(File(context.filesDir, "scheduled-reminders.json"))
        val meant = Instant.now().minusSeconds(5 * 3600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS) // should have fired five hours ago
        context.sendBroadcast(
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_FIRE)
                .putExtra(ReminderReceiver.EXTRA_ID, id)
                .putExtra(ReminderReceiver.EXTRA_TITLE, "Daily")
                .putExtra(ReminderReceiver.EXTRA_REPEAT, "Every Day")
                .putExtra(ReminderReceiver.EXTRA_REMIND_AT, meant.toEpochMilli()),
        )

        val expected = meant.atZone(Clock.systemDefaultZone().zone).plusDays(1).toInstant()
        val deadline = System.currentTimeMillis() + 5_000
        var next: Instant? = null
        while (System.currentTimeMillis() < deadline && next == null) {
            next = appStore.all().firstOrNull { it.id == id }?.remindAt
            if (next == null) Thread.sleep(100)
        }

        // Exactly one day after the time it was meant for: the five hours of lateness are not added on.
        assertEquals(expected, next)
        assertNotNull(existingAlarm())
    }
}
