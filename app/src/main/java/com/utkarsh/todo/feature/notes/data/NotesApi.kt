package com.utkarsh.todo.feature.notes.data

import com.utkarsh.todo.core.common.resultOf
import com.utkarsh.todo.feature.notes.domain.NotesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable data class NoteDto(val date: String, val text: String)

@Serializable data class SaveNoteRequest(val text: String)

interface NotesApi {
    @GET("notes/{date}")
    suspend fun get(@Path("date") date: String): NoteDto

    @PUT("notes/{date}")
    suspend fun put(@Path("date") date: String, @Body body: SaveNoteRequest): NoteDto
}

class NotesRepositoryImpl @Inject constructor(private val api: NotesApi) : NotesRepository {
    override suspend fun note(day: LocalDate) = resultOf { api.get(day.toString()).text }
    override suspend fun save(day: LocalDate, text: String) = resultOf { api.put(day.toString(), SaveNoteRequest(text)); Unit }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotesBindings {
    @Binds
    abstract fun repository(impl: NotesRepositoryImpl): NotesRepository

    companion object {
        @Provides
        @Singleton
        fun api(retrofit: Retrofit): NotesApi = retrofit.create()
    }
}
