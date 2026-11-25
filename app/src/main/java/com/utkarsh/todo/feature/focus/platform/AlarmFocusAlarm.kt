package com.utkarsh.todo.feature.focus.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.utkarsh.todo.feature.focus.domain.FocusAlarm
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** [FocusAlarm] backed by [AlarmManager]: exact when the person allowed it, otherwise within a few minutes. */
@Singleton
class AlarmFocusAlarm @Inject constructor(@ApplicationContext private val context: Context) : FocusAlarm {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Instant, title: String) {
        val pending = checkNotNull(pendingIntent(title, PendingIntent.FLAG_UPDATE_CURRENT))
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        }
    }

    override fun cancel() {
        pendingIntent("", PendingIntent.FLAG_NO_CREATE)?.let { pending ->
            alarms.cancel(pending)
            pending.cancel()
        }
    }

    private fun pendingIntent(title: String, flag: Int): PendingIntent? {
        val intent = Intent(context, FocusTimerReceiver::class.java)
            .setAction(FocusTimerReceiver.ACTION_DONE)
            .putExtra(FocusTimerReceiver.EXTRA_TITLE, title)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        /** One timer, so one fixed token. */
        const val REQUEST_CODE = 7001
    }
}
