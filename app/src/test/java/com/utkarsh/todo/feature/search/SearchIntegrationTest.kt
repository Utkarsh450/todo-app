package com.utkarsh.todo.feature.search

import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.projects.data.ProjectsApi
import com.utkarsh.todo.feature.projects.data.ProjectsRepositoryImpl
import com.utkarsh.todo.feature.search.ui.SearchPhase
import com.utkarsh.todo.feature.search.ui.SearchViewModel
import com.utkarsh.todo.feature.tasks.data.TasksApi
import com.utkarsh.todo.feature.tasks.data.TasksRepositoryImpl
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.create

/** Search through the real repositories, Retrofit and JSON against a fake server that filters like the backend does. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchIntegrationTest {
    private lateinit var server: MockWebServer
    private val queries = mutableListOf<String?>()
    private val clock = Clock.fixed(Instant.parse("2026-11-07T05:30:00Z"), ZoneId.of("Asia/Kolkata"))

    private fun task(id: String, title: String, project: String? = null) = """
        {"id":"$id","title":"$title","notes":"","projectId":${project?.let { "\"$it\"" }},"priority":"NORMAL","status":"TODO","dueAt":null,"completedAt":null,
         "archived":false,"pinned":false,"repeatRule":null,"createdAt":"2026-11-01T00:00:00.000Z","updatedAt":"2026-11-01T00:00:00.000Z","subtasks":[]}
    """.trimIndent()

    private val all = listOf("Pay invoice" to "p1", "Plan trip" to null, "Invoice review" to null)

    @Before
    fun setUp() {
        // Real time: the view-model waits 300 ms after the last key, and a test dispatcher would keep that clock frozen.
        Dispatchers.setMain(Dispatchers.Default)
        server = MockWebServer().also {
            it.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val json = { body: String -> MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build() }
                    return when (request.url.encodedPath) {
                        "/api/projects" -> json("""[{"id":"p1","name":"Finance","color":"#2FA465","createdAt":"2026-11-01T00:00:00.000Z"}]""")
                        "/api/tasks" -> {
                            val q = request.url.queryParameter("q")
                            queries += q
                            val hits = all.withIndex().filter { (_, t) -> q == null || t.first.contains(q, ignoreCase = true) }
                            json(hits.joinToString(",", "[", "]") { (i, t) -> task("t$i", t.first, t.second) })
                        }
                        else -> MockResponse.Builder().code(404).build()
                    }
                }
            }
            it.start()
        }
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(): SearchViewModel {
        val retrofit = buildRetrofit(server.url("/api/").toString())
        return SearchViewModel(TasksRepositoryImpl(retrofit.create<TasksApi>()), ProjectsRepositoryImpl(retrofit.create<ProjectsApi>()), clock)
    }

    @Test
    fun `typing a word sends it as q and shows the matching tasks with their project`() = runBlocking {
        val vm = viewModel()
        vm.onQueryChange("invoice")
        val state = withTimeout(5_000) { vm.uiState.first { it.phase == SearchPhase.RESULTS } }

        assertEquals(listOf("invoice"), queries)
        assertEquals(listOf("Pay invoice", "Invoice review"), state.results.map { it.title })
        assertEquals("Finance", state.results[0].projectName)
    }

    @Test
    fun `a word nothing matches gives the empty state`() = runBlocking {
        val vm = viewModel()
        vm.onQueryChange("zebra")
        withTimeout(5_000) { vm.uiState.first { it.phase == SearchPhase.EMPTY } }
        assertEquals(listOf<String?>("zebra"), queries)
    }
}
