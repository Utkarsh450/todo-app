package com.utkarsh.todo.feature.projects

import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.projects.data.ProjectsApi
import com.utkarsh.todo.feature.projects.data.ProjectsRepositoryImpl
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.create

/** Integration test of Retrofit + kotlinx.serialization + repository against a fake HTTP server. */
class ProjectsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: ProjectsRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        repository = ProjectsRepositoryImpl(buildRetrofit(server.url("/api/").toString()).create<ProjectsApi>())
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun `lists projects and ignores unknown server fields`() = runTest {
        server.enqueue(json("""[{"id":"1","name":"Design","color":"#935EDF","createdAt":"2026-11-07T00:00:00.000Z"}]"""))

        val projects = repository.projects().getOrThrow()

        assertEquals("Design", projects.single().name)
        assertEquals("#935EDF", projects.single().colorHex)
        assertEquals("/api/projects", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `create posts name and colour`() = runTest {
        server.enqueue(json("""{"id":"2","name":"Home","color":"#112233","createdAt":"2026-11-07T00:00:00.000Z"}""", 201))

        val created = repository.create("Home", "#112233").getOrThrow()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("""{"name":"Home","color":"#112233"}""", request.body?.utf8())
        assertEquals("2", created.id)
    }

    @Test
    fun `http errors become failures instead of throwing`() = runTest {
        server.enqueue(json("""{"error":"CONFLICT"}""", 409))

        val result = repository.create("Home", "#112233")

        assertTrue(result.isFailure)
    }

    @Test
    fun `update sends only the fields that were given`() = runTest {
        server.enqueue(json("""{"id":"2","name":"Home 2","color":"#112233","createdAt":"2026-11-07T00:00:00.000Z"}"""))

        val renamed = repository.update("2", name = "Home 2").getOrThrow()

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/projects/2", request.url.encodedPath)
        assertEquals("""{"name":"Home 2"}""", request.body?.utf8())
        assertEquals("Home 2", renamed.name)
    }

    @Test
    fun `update can change the colour alone`() = runTest {
        server.enqueue(json("""{"id":"2","name":"Home","color":"#D9548B","createdAt":"2026-11-07T00:00:00.000Z"}"""))

        repository.update("2", colorHex = "#D9548B").getOrThrow()

        assertEquals("""{"color":"#D9548B"}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun `delete calls the server and a missing project is a failure`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        assertTrue(repository.delete("2").isSuccess)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/projects/2", request.url.encodedPath)

        server.enqueue(json("""{"error":"NOT_FOUND"}""", 404))
        assertTrue(repository.delete("2").isFailure)
    }
}
