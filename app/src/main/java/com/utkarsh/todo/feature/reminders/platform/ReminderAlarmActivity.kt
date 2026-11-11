package com.utkarsh.todo.feature.reminders.platform

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.utkarsh.todo.MainActivity
import com.utkarsh.todo.core.common.formatTime
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.reminders.domain.AlertStyle
import com.utkarsh.todo.feature.reminders.ui.ReminderAlertScreen
import com.utkarsh.todo.feature.reminders.ui.ReminderAlertUi
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The full-screen alert for a ringing reminder. It opens over the lock screen and turns the screen on; the sound
 * is the notification's own and stops when this screen dismisses or snoozes the reminder (or the person leaves with Back).
 */
@AndroidEntryPoint
class ReminderAlarmActivity : ComponentActivity() {
    @Inject lateinit var notifications: ReminderNotifications
    @Inject lateinit var snoozer: ReminderSnoozer
    @Inject lateinit var done: ReminderDone
    @Inject lateinit var clock: Clock

    private var id = ""
    private var taskIdForDone: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        id = intent.getStringExtra(ReminderReceiver.EXTRA_ID).orEmpty()
        if (id.isEmpty()) { finish(); return }
        val title = intent.getStringExtra(ReminderReceiver.EXTRA_TITLE).orEmpty().ifBlank { "Reminder" }
        val dueAt = Instant.ofEpochMilli(intent.getLongExtra(ReminderReceiver.EXTRA_REMIND_AT, clock.millis()))
        val repeat = intent.getStringExtra(ReminderReceiver.EXTRA_REPEAT)
        val taskId = intent.getStringExtra(ReminderReceiver.EXTRA_TASK_ID)
        taskIdForDone = taskId
        val style = ReminderReceiver.styleOf(intent.getStringExtra(ReminderReceiver.EXTRA_STYLE))
        val ui = alertUi(title, dueAt, repeat, taskId != null)

        setContent {
            PaperTheme {
                BackHandler { dismiss() }
                ReminderAlertScreen(
                    ui = ui,
                    onSnooze = { minutes ->
                        notifications.cancel(id)
                        lifecycleScope.launch {
                            snoozer.snooze(id, title, dueAt, repeat, taskId, minutes, style)
                            finishAndRemoveTask()
                        }
                    },
                    onDismiss = ::complete,
                    snoozedText = { minutes -> "Back at " + clock.instant().plusSeconds(minutes * 60L).formatTime(clock.zone) },
                    onOpen = {
                        notifications.cancel(id)
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                        finishAndRemoveTask()
                    },
                )
            }
        }
    }

    /** Back leaves the alert as it is (only the sound stops); Done also ticks off the task the alert belongs to. */
    private fun dismiss() {
        notifications.cancel(id)
        finishAndRemoveTask()
    }

    private fun complete() {
        notifications.cancel(id)
        lifecycleScope.launch {
            done.complete(taskIdForDone)
            finishAndRemoveTask()
        }
    }

    private fun alertUi(title: String, dueAt: Instant, repeat: String?, hasTask: Boolean): ReminderAlertUi {
        val zone = clock.zone
        val minutesAgo = Duration.between(dueAt, clock.instant()).toMinutes()
        val status = when {
            repeat != null && minutesAgo < 1 -> "Due now · ${repeat.lowercase()}"
            minutesAgo < 1 -> "Due now"
            minutesAgo < 60 -> "$minutesAgo min ago"
            else -> "${minutesAgo / 60} hr ago"
        }
        return ReminderAlertUi(
            title = title,
            time = dueAt.formatTime(zone),
            date = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH).format(dueAt.atZone(zone)),
            status = status,
            canOpenTask = hasTask,
        )
    }

    companion object {
        /** An intent that opens the alert for the given reminder. */
        fun intent(context: android.content.Context, id: String, title: String, dueAtMillis: Long, repeatRule: String?, taskId: String?, style: AlertStyle = AlertStyle.ALARM): Intent =
            Intent(context, ReminderAlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(ReminderReceiver.EXTRA_ID, id)
                .putExtra(ReminderReceiver.EXTRA_TITLE, title)
                .putExtra(ReminderReceiver.EXTRA_REMIND_AT, dueAtMillis)
                .putExtra(ReminderReceiver.EXTRA_REPEAT, repeatRule)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
                .putExtra(ReminderReceiver.EXTRA_STYLE, style.name)
    }
}
