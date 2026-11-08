package com.utkarsh.todo.feature.calendar.data

import com.utkarsh.todo.feature.calendar.domain.CalendarRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.create

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarBindings {
    @Binds
    abstract fun repository(impl: CalendarRepositoryImpl): CalendarRepository

    companion object {
        @Provides
        @Singleton
        fun api(retrofit: Retrofit): CalendarApi = retrofit.create()
    }
}
