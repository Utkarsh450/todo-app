package com.utkarsh.todo.feature.reminders.domain

/**
 * What this phone has registered alarms for, kept on the device. After a reboot the alarms are gone and the
 * backend may be unreachable, so they are put back from here without the network.
 */
interface ReminderAlarmStore {
    fun put(reminder: Reminder)
    fun remove(id: String)
    fun all(): List<Reminder>
}
