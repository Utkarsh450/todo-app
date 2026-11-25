package com.utkarsh.todo.core.offline

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request

/** Asks [OfflineCacheInterceptor] to answer a GET from the saved copy only. Set by [CacheFirstCallFactory], removed by the interceptor. */
const val CACHE_FIRST_HEADER = "X-Cache-First"

/** Added to an answer that came from the saved copy because the caller asked for it with [cacheFirst] (not because we are offline). */
const val SAVED_FIRST_HEADER = "X-Saved-First"

/** Whether the code running on this thread is inside a [cacheFirst] block. Fakes in tests can read it to play the saved copy. */
object CacheFirst {
    private val active = ThreadLocal<Boolean?>()

    fun isActive(): Boolean = active.get() == true

    internal fun element() = active.asContextElement(true)
}

/**
 * Runs [block] so every GET it makes (through Retrofit) is answered from the saved copy, instantly and without the network.
 * A GET with no saved copy fails with HTTP 504. Use it to put last time's data on screen while the real request is on its way:
 * `cacheFirst { repo.load() }` first, then `repo.load()` as usual. The mark follows the coroutine across threads and its children;
 * to run several reads in parallel inside the block, wrap them in `coroutineScope { async { ... } }` (a bare `async` would attach to the caller's scope).
 */
suspend fun <T> cacheFirst(block: suspend () -> T): T = withContext(CacheFirst.element()) { block() }

/** Marks GET calls created inside [cacheFirst]. OkHttp runs interceptors on its own threads, so the mark has to be made here, when the call is created on the caller's thread. */
class CacheFirstCallFactory(private val delegate: Call.Factory) : Call.Factory {
    override fun newCall(request: Request): Call {
        val marked = if (CacheFirst.isActive() && request.method == "GET") request.newBuilder().header(CACHE_FIRST_HEADER, "1").build() else request
        return delegate.newCall(marked)
    }
}
