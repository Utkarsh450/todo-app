package com.utkarsh.todo.core.common

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    /** Injectable clock so time-dependent logic (today, overdue, reminders) is testable. */
    @Provides
    fun clock(): Clock = Clock.system(ZoneId.systemDefault())
}
