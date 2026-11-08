package com.utkarsh.todo

import android.app.Application
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.reminders.platform.ReminderNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class TodoApplication : Application() {
    @Inject lateinit var notifications: ReminderNotifications
    @Inject lateinit var reminders: RemindersRepository

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannel()
        // Keep device alarms in step with the server; failures (offline) are retried next launch.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            reminders.restoreAlarms() // works offline
            reminders.syncAlarms()
        }
    }
}
