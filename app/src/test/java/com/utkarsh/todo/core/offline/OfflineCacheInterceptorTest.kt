package com.utkarsh.todo.core.offline

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineScope
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The offline layer against a real HTTP server that can be switched off, like a phone losing its connection. */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineCacheInterceptorTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var monitor: OfflineMonitor
    private lateinit var store: FileOfflineStore
    private lateinit var client: OkHttpClient
    private lateinit var base: String

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        base = server.url("/api/").toString()
        monitor = OfflineMonitor()
        store = FileOfflineStore(File(temp.root, "cache"))
        client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .addInterceptor(OfflineCacheInterceptor(store, monitor))
            .build()
    }

    @After
    fun tearDown() = runCatching { server.close() }.let { }

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private fun get(path: String) = client.newCall(Request.Builder().url(base + path).build()).execute()

    @Test
    fun `online it behaves like a normal client and the body is still readable`() {
        server.enqueue(json("""[{"id":1}]"""))
        get("tasks").use { response ->
            assertEquals(200, response.code)
            assertEquals("""[{"id":1}]""", response.body.string())
            assertNull(response.header(OFFLINE_CACHE_HEADER))
        }
        assertFalse(monitor.offline.value)
    }

    @Test
    fun `when the server cannot be reached the last saved answer is served and we go offline`() {
        server.enqueue(json("""[{"id":1}]"""))
        get("tasks").use { it.body.string() }
        server.close()

        get("tasks").use { response ->
            assertEquals(200, response.code)
            assertEquals("1", response.header(OFFLINE_CACHE_HEADER))
            assertEquals("""[{"id":1}]""", response.body.string())
            assertEquals("application/json", response.body.contentType()?.let { "${it.type}/${it.subtype}" })
        }
        assertTrue(monitor.offline.value)
    }

    @Test
    fun `a url that was never saved fails as before but still marks us offline`() {
        server.close()
        try {
            get("calendar/agenda").close()
            fail("expected an IOException")
        } catch (_: IOException) {
        }
        assertTrue(monitor.offline.value)
    }

    @Test
    fun `each url, query included, has its own saved answer`() {
        server.enqueue(json("""["monday"]"""))
        server.enqueue(json("""["tuesday"]"""))
        get("tasks?from=2026-11-02").use { it.body.string() }
        get("tasks?from=2026-11-03").use { it.body.string() }
        server.close()

        assertEquals("""["monday"]""", get("tasks?from=2026-11-02").use { it.body.string() })
        assertEquals("""["tuesday"]""", get("tasks?from=2026-11-03").use { it.body.string() })
    }

    @Test
    fun `the newest good answer replaces the older one`() {
        server.enqueue(json("""["old"]"""))
        server.enqueue(json("""["new"]"""))
        get("tasks").use { it.body.string() }
        get("tasks").use { it.body.string() }
        server.close()
        assertEquals("""["new"]""", get("tasks").use { it.body.string() })
    }

    @Test
    fun `an error answer is passed on, never saved, and does not replace a good copy`() {
        server.enqueue(json("""["good"]"""))
        server.enqueue(json("""{"error":"INTERNAL"}""", 500))
        get("tasks").use { it.body.string() }
        get("tasks").use { response ->
            assertEquals(500, response.code)
            assertNull(response.header(OFFLINE_CACHE_HEADER))
        }
        assertFalse(monitor.offline.value) // the server did answer
        server.close()
        assertEquals("""["good"]""", get("tasks").use { it.body.string() })
    }

    @Test
    fun `writes are never answered from the saved copy`() {
        server.enqueue(json("""{"id":"1"}""", 201))
        client.newCall(Request.Builder().url(base + "tasks").post("{}".toRequestBody()).build()).execute().use { it.body.string() }
        server.close()
        try {
            client.newCall(Request.Builder().url(base + "tasks").post("{}".toRequestBody()).build()).execute().close()
            fail("expected an IOException")
        } catch (_: IOException) {
        }
        assertFalse(monitor.offline.value) // a failed write alone does not flip the banner
    }

    @Test
    fun `health is never saved or faked so it can tell whether the server is back`() {
        server.enqueue(json("""{"status":"ok"}"""))
        get("health").use { it.body.string() }
        server.close()
        try {
            get("health").close()
            fail("expected an IOException")
        } catch (_: IOException) {
        }
    }

    @Test
    fun `coming back online clears the flag and announces it once`() = runTest {
        val seen = mutableListOf<Unit>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        monitor.reconnected.onEach { seen += it }.launchIn(scope)

        server.enqueue(json("[]"))
        get("tasks").use { it.body.string() }
        val port = server.port
        server.close()
        get("tasks").use { it.body.string() }
        assertTrue(monitor.offline.value)
        assertTrue(seen.isEmpty())

        server = MockWebServer().also { it.start(port) }
        server.enqueue(json("[]"))
        server.enqueue(json("[]"))
        get("tasks").use { it.body.string() }
        get("tasks").use { it.body.string() }

        assertFalse(monitor.offline.value)
        assertEquals(1, seen.size)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun `a call the app cancelled is not mistaken for losing the connection`() {
        server.enqueue(json("[]"))
        get("tasks").use { it.body.string() }
        val call = client.newCall(Request.Builder().url(base + "tasks").build())
        call.cancel()
        try {
            call.execute().close()
            fail("expected an IOException")
        } catch (_: IOException) {
        }
        assertFalse(monitor.offline.value)
    }

    private fun getCacheFirst(path: String) =
        client.newCall(Request.Builder().url(base + path).header(CACHE_FIRST_HEADER, "1").build()).execute()

    @Test
    fun `cache first answers from the saved copy without calling the server`() {
        server.enqueue(json("""["saved"]"""))
        get("tasks").use { it.body.string() }
        val callsBefore = server.requestCount

        getCacheFirst("tasks").use { response ->
            assertEquals(200, response.code)
            assertEquals("""["saved"]""", response.body.string())
            assertEquals("1", response.header(SAVED_FIRST_HEADER))
            assertNull(response.header(OFFLINE_CACHE_HEADER)) // this is not an offline answer
        }
        assertEquals(callsBefore, server.requestCount)
        assertFalse(monitor.offline.value)
    }

    @Test
    fun `cache first with nothing saved answers 504 and does not call the server`() {
        getCacheFirst("tasks").use { response ->
            assertEquals(504, response.code)
            assertNull(response.header(SAVED_FIRST_HEADER))
        }
        assertEquals(0, server.requestCount)
        assertFalse(monitor.offline.value)
    }

    @Test
    fun `cache first never flips the offline state even when the server is gone`() {
        server.enqueue(json("[1]"))
        get("tasks").use { it.body.string() }
        server.close()

        getCacheFirst("tasks").use { assertEquals(200, it.code) }
        getCacheFirst("projects").use { assertEquals(504, it.code) }
        assertFalse(monitor.offline.value)
    }

    @Test
    fun `a changing now parameter does not make every call a different saved copy`() {
        server.enqueue(json("""["grouped"]"""))
        get("reminders/grouped?now=2026-11-25T04:27:24.388556Z&endOfToday=2026-11-25T18:30:00Z").use { it.body.string() }

        // a later call: another instant, same day boundaries
        getCacheFirst("reminders/grouped?now=2026-11-25T06:01:02.000001Z&endOfToday=2026-11-25T18:30:00Z").use { response ->
            assertEquals(200, response.code)
            assertEquals("""["grouped"]""", response.body.string())
        }
        // a different day is still a different copy
        getCacheFirst("reminders/grouped?now=2026-11-26T04:00:00Z&endOfToday=2026-11-26T18:30:00Z").use { assertEquals(504, it.code) }

        server.close() // and offline fallback benefits too
        get("reminders/grouped?now=2026-11-25T09:00:00Z&endOfToday=2026-11-25T18:30:00Z").use { assertEquals("""["grouped"]""", it.body.string()) }
    }

    @Test
    fun `the cache first marker is removed before a write reaches the server`() {
        server.enqueue(json("""{"id":"1"}""", 201))
        client.newCall(
            Request.Builder().url(base + "tasks").header(CACHE_FIRST_HEADER, "1").post("{}".toRequestBody()).build(),
        ).execute().use { assertEquals(201, it.code) }

        assertNull(server.takeRequest().headers[CACHE_FIRST_HEADER])
    }

    @Test
    fun `a broken saved file is ignored`() {
        server.enqueue(json("[1]"))
        get("tasks").use { it.body.string() }
        File(temp.root, "cache").listFiles()!!.forEach { it.writeBytes(byteArrayOf(1, 2, 3)) } // no newline: not our format
        server.close()
        try {
            get("tasks").close()
            fail("expected an IOException")
        } catch (_: IOException) {
        }
    }
}
