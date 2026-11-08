package com.utkarsh.todo.feature.reminders.platform

import android.content.Context
import com.utkarsh.todo.feature.reminders.domain.ReminderAlarmStore
import com.utkarsh.todo.feature.reminders.domain.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersPlatformModule {
    @Binds
    abstract fun scheduler(impl: StoringReminderScheduler): ReminderScheduler

    companion object {
        @Provides
        @Singleton
        fun alarmStore(@ApplicationContext context: Context): ReminderAlarmStore =
            FileReminderAlarmStore(File(context.filesDir, "scheduled-reminders.json"))
    }
}
