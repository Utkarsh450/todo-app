package com.utkarsh.todo.feature.ai.data

import com.utkarsh.todo.core.common.resultOf
import com.utkarsh.todo.core.network.ApiJson
import com.utkarsh.todo.core.network.InstantSerializer
import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.BuildConfig
import com.utkarsh.todo.feature.ai.domain.AiException
import com.utkarsh.todo.feature.ai.domain.AiRepository
import com.utkarsh.todo.feature.ai.domain.AssistantReply
import com.utkarsh.todo.feature.ai.domain.AssistantSuggestion
import com.utkarsh.todo.feature.ai.domain.DumpedTask
import com.utkarsh.todo.feature.tasks.domain.Priority
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
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

@Serializable data class AssistRequest(val message: String, val timeZone: String)

/** One flat shape for every kind; [toDomain] picks out the fields that kind needs and drops anything incomplete. */
@Serializable
data class SuggestionDto(
    val kind: String,
    val taskId: String? = null,
    val taskTitle: String? = null,
    val title: String? = null,
    val reason: String = "",
    @Serializable(with = InstantSerializer::class) val dueAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val startAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val endAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val remindAt: Instant? = null,
    val allDay: Boolean = false,
    val steps: List<String>? = null,
    val priority: String? = null,
    val repeatRule: String? = null,
    val projectId: String? = null,
    val projectName: String? = null,
) {
    fun toDomain(): AssistantSuggestion? {
        val id = taskId
        val name = taskTitle.orEmpty()
        val label = title?.takeIf { it.isNotBlank() }
        val level = priority?.let { p -> Priority.entries.firstOrNull { it.name == p } }
        return when (kind) {
            "MOVE" -> if (id != null) dueAt?.let { AssistantSuggestion.Move(id, name, reason, it) } else null
            "SPLIT" -> if (id != null) steps?.takeIf { it.isNotEmpty() }?.let { AssistantSuggestion.Split(id, name, reason, it) } else null
            "PRIORITY" -> if (id != null) level?.let { AssistantSuggestion.ChangePriority(id, name, reason, it) } else null
            "COMPLETE" -> id?.let { AssistantSuggestion.Complete(it, name, reason) }
            "CREATE_TASK" -> label?.let { AssistantSuggestion.CreateTask(it, reason, dueAt, level ?: Priority.NORMAL, repeatRule, projectId, projectName) }
            "CREATE_EVENT" -> if (label != null && startAt != null) {
                AssistantSuggestion.CreateEvent(label, reason, startAt, endAt?.takeIf { it > startAt } ?: startAt.plusSeconds(if (allDay) 86_400 else 3_600), allDay)
            } else null
            "CREATE_REMINDER" -> if (label != null && remindAt != null) AssistantSuggestion.CreateReminder(label, reason, remindAt, repeatRule) else null
            else -> null
        }
    }
}

@Serializable data class AssistResponse(val reply: String, val suggestions: List<SuggestionDto> = emptyList())

@Serializable data class BrainDumpRequest(val text: String, val timeZone: String)

@Serializable
data class DumpedTaskDto(
    val title: String,
    @Serializable(with = InstantSerializer::class) val dueAt: Instant? = null,
    val priority: String = "NORMAL",
    val repeatRule: String? = null,
    val projectId: String? = null,
    val projectName: String? = null,
) {
    fun toDomain() = DumpedTask(
        title = title,
        dueAt = dueAt,
        priority = Priority.entries.firstOrNull { it.name == priority } ?: Priority.NORMAL,
        repeatRule = repeatRule,
        projectId = projectId,
        projectName = projectName,
    )
}

@Serializable data class BrainDumpResponse(val tasks: List<DumpedTaskDto> = emptyList())

interface AiApi {
    @POST("ai/braindump")
    suspend fun brainDump(@Body body: BrainDumpRequest): BrainDumpResponse

    @POST("ai/assist")
    suspend fun assist(@Body body: AssistRequest): AssistResponse

    @POST("ai/breakdown")
    suspend fun breakdown(@Body body: BreakdownRequest): BreakdownResponse
}

@Singleton
class AiRepositoryImpl @Inject constructor(private val api: AiApi) : AiRepository {
    override suspend fun breakDown(title: String, notes: String, dueAt: Instant?): Result<List<String>> =
        resultOf { api.breakdown(BreakdownRequest(title, notes, dueAt)).subtasks }
            .recoverCatching { throw it.toAiException() }

    override suspend fun brainDump(text: String, zone: ZoneId): Result<List<DumpedTask>> =
        resultOf { api.brainDump(BrainDumpRequest(text, zone.id)).tasks.map(DumpedTaskDto::toDomain) }
            .recoverCatching { throw it.toAiException() }

    override suspend fun assist(message: String, zone: ZoneId): Result<AssistantReply> =
        resultOf { api.assist(AssistRequest(message, zone.id)).let { r -> AssistantReply(r.reply, r.suggestions.mapNotNull(SuggestionDto::toDomain)) } }
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
            code == "VALIDATION_ERROR" -> AiException("The assistant couldn't read that request.")
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
