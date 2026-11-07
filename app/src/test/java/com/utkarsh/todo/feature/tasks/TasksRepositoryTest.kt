package com.utkarsh.todo.feature.tasks

import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.tasks.data.TasksApi
import com.utkarsh.todo.feature.tasks.data.TasksRepositoryImpl
import com.utkarsh.todo.feature.tasks.domain.Field
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.create

class TasksRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: TasksRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        repository = TasksRepositoryImpl(buildRetrofit(server.url("/api/").toString()).create<TasksApi>())
    }

    @After
    fun tearDown() = server.close()

    private fun task(id: String = "t1", status: String = "TODO", due: String? = "2026-11-07T09:00:00.000Z") = """
        {"id":"$id","title":"Review PR","notes":"","projectId":null,"priority":"HIGH","status":"$status",
         "dueAt":${due?.let { "\"$it\"" }},"completedAt":null,"archived":false,"pinned":false,"repeatRule":null,
         "createdAt":"2026-11-01T00:00:00.000Z","updatedAt":"2026-11-01T00:00:00.000Z",
         "subtasks":[{"id":"s1","taskId":"$id","title":"Read diff","done":true,"position":0}]}
    """.trimIndent()

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun `dueBetween maps dto to domain and sends ISO range`() = runTest {
        server.enqueue(json("[${task()}]"))

        val tasks = repository.dueBetween(Instant.parse("2026-11-07T00:00:00Z"), Instant.parse("2026-11-08T00:00:00Z")).getOrThrow()

        val task = tasks.single()
        assertEquals(Priority.HIGH, task.priority)
        assertEquals(Instant.parse("2026-11-07T09:00:00Z"), task.dueAt)
        assertEquals(1, task.subtasksDone)
        val url = server.takeRequest().url
        assertEquals("2026-11-07T00:00:00Z", url.queryParameter("from"))
        assertEquals("2026-11-08T00:00:00Z", url.queryParameter("to"))
    }

    @Test
    fun `handles tasks without a due date`() = runTest {
        server.enqueue(json("[${task(due = null)}]"))
        assertNull(repository.search("review").getOrThrow().single().dueAt)
        assertEquals("review", server.takeRequest().url.queryParameter("q"))
    }

    @Test
    fun `create posts the task with subtasks`() = runTest {
        server.enqueue(json(task(), 201))

        repository.create(NewTask(title = "Review PR", priority = Priority.HIGH, subtasks = listOf("Read diff"))).getOrThrow()

        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("Review PR", body["title"].toString().trim('"'))
        assertEquals("HIGH", body["priority"].toString().trim('"'))
        assertEquals(1, (body["subtasks"] as kotlinx.serialization.json.JsonArray).size)
    }

    @Test
    fun `patch omits unset fields and sends explicit nulls`() = runTest {
        server.enqueue(json(task()))

        repository.update("t1", TaskPatch(dueAt = Field.Set(null), archived = Field.Set(true))).getOrThrow()

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        val body: JsonObject = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals(setOf("dueAt", "archived"), body.keys)
        assertEquals("null", body["dueAt"].toString())
        assertEquals("true", body["archived"].toString())
    }

    @Test
    fun `setDone patches the status`() = runTest {
        server.enqueue(json(task(status = "DONE")))

        val done = repository.setDone("t1", true).getOrThrow()

        assertEquals(TaskStatus.DONE, done.status)
        assertTrue(done.isDone)
        assertEquals("""{"status":"DONE"}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun `subtask toggle and add hit the right endpoints`() = runTest {
        server.enqueue(json("""{"id":"s2","taskId":"t1","title":"Write","done":false,"position":1}""", 201))
        server.enqueue(json("""{"id":"s2","taskId":"t1","title":"Write","done":true,"position":1}"""))

        assertEquals("s2", repository.addSubtask("t1", "Write").getOrThrow().id)
        assertTrue(repository.setSubtaskDone("s2", true).getOrThrow().done)

        assertEquals("/api/tasks/t1/subtasks", server.takeRequest().url.encodedPath)
        assertEquals("/api/subtasks/s2", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `server errors surface as failures`() = runTest {
        server.enqueue(json("""{"error":"NOT_FOUND"}""", 404))
        assertTrue(repository.get("missing").isFailure)
        server.enqueue(json("""{"error":"NOT_FOUND"}""", 404))
        assertFalse(repository.delete("missing").isSuccess)
    }
}
