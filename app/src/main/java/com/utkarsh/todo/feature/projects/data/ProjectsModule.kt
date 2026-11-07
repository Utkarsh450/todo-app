package com.utkarsh.todo.feature.projects.data

import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProjectsBindings {
    @Binds
    abstract fun repository(impl: ProjectsRepositoryImpl): ProjectsRepository

    companion object {
        @Provides
        @Singleton
        fun api(retrofit: Retrofit): ProjectsApi = retrofit.create()
    }
}
