package com.utkarsh.todo.feature.reminders.data

import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
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
abstract class RemindersBindings {
    @Binds
    abstract fun repository(impl: RemindersRepositoryImpl): RemindersRepository

    companion object {
        @Provides
        @Singleton
        fun api(retrofit: Retrofit): RemindersApi = retrofit.create()
    }
}
