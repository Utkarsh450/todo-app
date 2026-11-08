package com.utkarsh.todo.feature.reminders.platform

import com.utkarsh.todo.feature.reminders.domain.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersPlatformModule {
    @Binds
    abstract fun scheduler(impl: AlarmReminderScheduler): ReminderScheduler
}
