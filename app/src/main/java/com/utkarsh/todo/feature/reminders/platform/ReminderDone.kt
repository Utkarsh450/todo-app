package com.utkarsh.todo.feature.reminders.platform

import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import javax.inject.Inject
import javax.inject.Singleton

/** What "Done" on an alert does: if the alert belongs to a task, the task is ticked off. Events and stand-alone alerts have nothing to complete. */
@Singleton
class ReminderDone @Inject constructor(private val tasks: TasksRepository) {
    suspend fun complete(taskId: String?) {
        if (taskId != null) tasks.setDone(taskId, true)
    }
}
