package com.utkarsh.todo.feature.reminders.platform

import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.ReminderAlarmStore
import com.utkarsh.todo.feature.reminders.domain.ReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton

/** Registers OS alarms and remembers them on the device, so they can be restored after a reboot without the network. */
@Singleton
class StoringReminderScheduler @Inject constructor(
    private val alarms: AlarmReminderScheduler,
    private val store: ReminderAlarmStore,
) : ReminderScheduler {
    override fun schedule(reminder: Reminder) {
        alarms.schedule(reminder)
        store.put(reminder)
    }

    override fun cancel(id: String) {
        alarms.cancel(id)
        store.remove(id)
    }
}
