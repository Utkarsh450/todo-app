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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

/** `PUT` replaces the whole note, so adding to it means reading it first and writing back the combined text. */
internal fun appendToNote(existing: String, addition: String): String {
    val added = addition.trim()
    return if (existing.isBlank()) added else existing.trimEnd() + "\n" + added
}

@Singleton
class NotesRepositoryImpl @Inject constructor(private val api: NotesApi) : NotesRepository {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override suspend fun note(day: LocalDate) = resultOf { api.get(day.toString()).text }
    override suspend fun save(day: LocalDate, text: String) = resultOf { api.put(day.toString(), SaveNoteRequest(text)); Unit }

    override suspend fun append(day: LocalDate, text: String) = resultOf {
        val existing = api.get(day.toString()).text
        api.put(day.toString(), SaveNoteRequest(appendToNote(existing, text)))
        Unit
    }.also { result -> result.onSuccess { _changes.tryEmit(Unit) } }
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
