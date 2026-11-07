package com.utkarsh.todo.feature.tasks.data

import com.utkarsh.todo.feature.tasks.domain.TasksRepository
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
abstract class TasksBindings {
    @Binds
    abstract fun repository(impl: TasksRepositoryImpl): TasksRepository

    companion object {
        @Provides
        @Singleton
        fun api(retrofit: Retrofit): TasksApi = retrofit.create()
    }
}
