package com.utkarsh.todo.feature.habits.data

import com.utkarsh.todo.feature.habits.domain.HabitsRepository
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
abstract class HabitsBindings {
    @Binds
    abstract fun repository(impl: HabitsRepositoryImpl): HabitsRepository

    companion object {
        @Provides
        @Singleton
        fun api(retrofit: Retrofit): HabitsApi = retrofit.create()
    }
}
