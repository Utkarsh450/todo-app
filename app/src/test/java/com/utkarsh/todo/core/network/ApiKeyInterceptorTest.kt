package com.utkarsh.todo.core.network

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ApiKeyInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        repeat(3) { server.enqueue(MockResponse.Builder().code(200).body("[]").build()) }
    }

    @After
    fun tearDown() = server.close()

    private fun call(key: String, build: Request.Builder.() -> Unit = {}) {
        val client = OkHttpClient.Builder().addInterceptor(ApiKeyInterceptor(key)).build()
        client.newCall(Request.Builder().url(server.url("/api/tasks")).apply(build).build()).execute().close()
    }

    @Test
    fun `sends the key in the X-Api-Key header on every request`() = runTest {
        call("secret-key-123")
        call("secret-key-123")

        assertEquals("secret-key-123", server.takeRequest().headers["X-Api-Key"])
        assertEquals("secret-key-123", server.takeRequest().headers["X-Api-Key"])
    }

    @Test
    fun `sends nothing when the app was built without a key`() = runTest {
        call("")
        call("   ")

        assertNull(server.takeRequest().headers["X-Api-Key"])
        assertNull(server.takeRequest().headers["X-Api-Key"])
    }

    @Test
    fun `leaves the rest of the request alone`() = runTest {
        call("k-123456") { header("Accept", "application/json") }

        val request = server.takeRequest()
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("/api/tasks", request.url.encodedPath)
    }
}
