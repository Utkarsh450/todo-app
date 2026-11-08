package com.utkarsh.todo.core.offline

import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.tasks.data.TasksApi
import com.utkarsh.todo.feature.tasks.data.TasksRepositoryImpl
import com.utkarsh.todo.feature.tasks.domain.Priority
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.create

/** A repository, real Retrofit and JSON, and the offline layer: read while the server is up, then lose it. */
class OfflineIntegrationTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val monitor = OfflineMonitor()
    private lateinit var repository: TasksRepositoryImpl

    private val from = Instant.parse("2026-11-07T18:30:00Z")
    private val to = Instant.parse("2026-11-08T18:30:00Z")

    private fun task(id: String) = """
        {"id":"$id","title":"Task $id","notes":"","projectId":null,"priority":"HIGH","status":"TODO","dueAt":"2026-11-08T09:00:00.000Z","completedAt":null,
         "archived":false,"pinned":false,"repeatRule":null,"createdAt":"2026-11-01T00:00:00.000Z","updatedAt":"2026-11-01T00:00:00.000Z","subtasks":[]}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val client = OkHttpClient.Builder().addInterceptor(OfflineCacheInterceptor(FileOfflineStore(File(temp.root, "c")), monitor)).build()
        repository = TasksRepositoryImpl(buildRetrofit(server.url("/api/").toString(), client).create<TasksApi>())
    }

    @After
    fun tearDown() = runCatching { server.close() }.let { }

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun `a day that was read before can be read again with the server gone`() = runTest {
        server.enqueue(json("[${task("a")},${task("b")}]"))
        val online = repository.dueBetween(from, to).getOrThrow()
        server.close()

        val offline = repository.dueBetween(from, to).getOrThrow()

        assertEquals(online, offline)
        assertEquals(Priority.HIGH, offline.first().priority)
        assertTrue(monitor.offline.value)
    }

    @Test
    fun `a day that was never read fails with the server gone`() = runTest {
        server.close()
        assertTrue(repository.dueBetween(from, to).isFailure)
    }

    @Test
    fun `editing still needs the server`() = runTest {
        server.enqueue(json("[${task("a")}]"))
        repository.dueBetween(from, to)
        server.close()

        assertTrue(repository.setDone("a", true).isFailure)
        assertTrue(repository.delete("a").isFailure)
    }

    @Test
    fun `a failed edit does not change what is read offline`() = runTest {
        server.enqueue(json("[${task("a")}]"))
        repository.dueBetween(from, to)
        server.close()
        repository.setDone("a", true)

        assertFalse(repository.dueBetween(from, to).getOrThrow().single().isDone)
    }
}
