package com.utkarsh.todo.feature.focus.platform

import com.utkarsh.todo.feature.focus.domain.FocusAlarm
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class FocusPlatformModule {
    @Binds
    abstract fun alarm(impl: AlarmFocusAlarm): FocusAlarm
}
