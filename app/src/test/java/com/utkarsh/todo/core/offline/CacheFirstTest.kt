package com.utkarsh.todo.core.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `cacheFirst { }` marks the GET calls made inside it; nothing outside it is touched. */
class CacheFirstTest {
    private val sent = mutableListOf<Request>()
    private val factory = CacheFirstCallFactory(
        object : Call.Factory {
            override fun newCall(request: Request): Call {
                sent += request
                return OkHttpClient().newCall(request)
            }
        },
    )

    private fun get(path: String = "tasks") = Request.Builder().url("http://localhost/api/$path").build()

    @Test
    fun `calls made outside cacheFirst are left alone`() {
        factory.newCall(get())
        assertNull(sent.single().header(CACHE_FIRST_HEADER))
        assertFalse(CacheFirst.isActive())
    }

    @Test
    fun `a GET made inside cacheFirst is marked`() = runTest {
        cacheFirst { factory.newCall(get()) }
        assertEquals("1", sent.single().header(CACHE_FIRST_HEADER))
    }

    @Test
    fun `writes inside cacheFirst are never marked`() = runTest {
        cacheFirst { factory.newCall(Request.Builder().url("http://localhost/api/tasks").post("{}".toRequestBody()).build()) }
        assertNull(sent.single().header(CACHE_FIRST_HEADER))
    }

    @Test
    fun `the mark follows the coroutine onto another thread`() = runTest {
        val seenOn = mutableListOf<String>()
        cacheFirst {
            withContext(Dispatchers.IO) {
                seenOn += Thread.currentThread().name
                factory.newCall(get("projects"))
            }
        }
        assertEquals("1", sent.single().header(CACHE_FIRST_HEADER))
        assertFalse("ran on the test thread, so this proves nothing: $seenOn", seenOn.single().contains("Test worker"))
    }

    @Test
    fun `the mark follows into async children made with coroutineScope inside the block`() = runTest {
        // A bare async inside the block would attach to the test's own scope, not to cacheFirst's coroutine: use coroutineScope.
        cacheFirst {
            coroutineScope {
                listOf(
                    async(Dispatchers.Default) { factory.newCall(get("reminders")) },
                    async(Dispatchers.IO) { factory.newCall(get("tasks")) },
                    async(Dispatchers.Default) { factory.newCall(get("projects")) },
                ).forEach { it.await() }
            }
        }
        assertEquals(3, sent.size)
        assertEquals(listOf("1", "1", "1"), sent.map { it.header(CACHE_FIRST_HEADER) })
    }

    @Test
    fun `the mark ends with the block`() = runTest {
        cacheFirst { assertTrue(CacheFirst.isActive()) }
        assertFalse(CacheFirst.isActive())
        factory.newCall(get())
        assertNull(sent.single().header(CACHE_FIRST_HEADER))
    }
}
