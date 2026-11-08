package com.utkarsh.todo.feature.reminders.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Alarms do not survive a reboot, so re-register them once the device has started. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: RemindersRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.restoreAlarms() // from this phone, no network needed
                repository.syncAlarms() // then catch up with the server if it can be reached
            } finally {
                pending.finish()
            }
        }
    }
}
