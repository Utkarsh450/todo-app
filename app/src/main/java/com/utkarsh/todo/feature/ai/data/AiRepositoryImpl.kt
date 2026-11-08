package com.utkarsh.todo.feature.ai.data

import com.utkarsh.todo.core.common.resultOf
import com.utkarsh.todo.core.network.ApiJson
import com.utkarsh.todo.core.network.InstantSerializer
import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.BuildConfig
import com.utkarsh.todo.feature.ai.domain.AiException
import com.utkarsh.todo.feature.ai.domain.AiRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class BreakdownRequest(
    val title: String,
    val notes: String,
    @Serializable(with = InstantSerializer::class) val dueAt: Instant? = null,
)

@Serializable data class BreakdownResponse(val subtasks: List<String>)

interface AiApi {
    @POST("ai/breakdown")
    suspend fun breakdown(@Body body: BreakdownRequest): BreakdownResponse
}

@Singleton
class AiRepositoryImpl @Inject constructor(private val api: AiApi) : AiRepository {
    override suspend fun breakDown(title: String, notes: String, dueAt: Instant?): Result<List<String>> =
        resultOf { api.breakdown(BreakdownRequest(title, notes, dueAt)).subtasks }
            .recoverCatching { throw it.toAiException() }
}

/** Turns a transport or HTTP failure into a message a person can act on. */
internal fun Throwable.toAiException(): Throwable = when (this) {
    is AiException -> this
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val parsed = body?.let { runCatching { ApiJson.parseToJsonElement(it).jsonObject }.getOrNull() }
        val code = parsed?.get("error")?.jsonPrimitive?.content
        val message = parsed?.get("message")?.jsonPrimitive?.content
        when {
            code == "AI_NOT_CONFIGURED" -> AiException(message ?: "AI is not set up on the server.", notConfigured = true)
            code == "VALIDATION_ERROR" -> AiException("This task can't be sent to the assistant.")
            message != null && code?.startsWith("AI_") == true -> AiException(message)
            else -> AiException("The server had a problem (${code()}). Try again.")
        }
    }
    is IOException -> AiException("Can't reach the server. Check that the backend is running and you're online.")
    else -> this
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindings {
    @Binds
    abstract fun repository(impl: AiRepositoryImpl): AiRepository

    companion object {
        /** The assistant can take a minute to think, so this call gets a longer read timeout than the rest of the API. */
        @Provides
        @Singleton
        fun api(client: OkHttpClient): AiApi =
            buildRetrofit(BuildConfig.API_BASE_URL, client.newBuilder().readTimeout(AI_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()).create()

        private const val AI_READ_TIMEOUT_SECONDS = 75L
    }
}
