package com.utkarsh.todo.core.offline

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Header added to an answer that came from the saved copy rather than the server. */
const val OFFLINE_CACHE_HEADER = "X-Offline-Cache"

/**
 * Makes reading work without the server: every successful GET is saved, and when a GET cannot connect the last saved
 * answer for the same URL is returned instead. Writes (POST/PATCH/DELETE) are never faked, so editing still needs the
 * server. `/health` is left alone because it is how we check whether the server is back.
 */
class OfflineCacheInterceptor(private val store: OfflineStore, private val monitor: OfflineMonitor) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val cacheFirst = original.header(CACHE_FIRST_HEADER) != null
        // The marker is ours: it never goes to the server.
        val request = if (cacheFirst) original.newBuilder().removeHeader(CACHE_FIRST_HEADER).build() else original
        if (request.method != "GET" || request.url.encodedPath.endsWith("/health")) return chain.proceed(request)
        val key = cacheKey(request)
        if (cacheFirst) {
            // Saved copy only, no network, and nothing here says anything about whether the server is reachable.
            val saved = store.read(key) ?: return unsatisfiable(request)
            return savedResponse(request, saved, SAVED_FIRST_HEADER)
        }
        try {
            val response = chain.proceed(request)
            monitor.markOnline() // the server answered, even if with an error
            if (!response.isSuccessful) return response
            val body = response.body
            val bytes = body.bytes()
            store.write(key, bytes, body.contentType()?.toString())
            return response.newBuilder().body(bytes.toResponseBody(body.contentType())).build()
        } catch (e: IOException) {
            // A call the app itself cancelled (a screen was left) says nothing about the network.
            if (chain.call().isCanceled()) throw e
            monitor.markOffline()
            val saved = store.read(key) ?: throw e
            return savedResponse(request, saved, OFFLINE_CACHE_HEADER)
        }
    }

    /**
     * One saved copy per URL, except that the `now` query parameter (the current instant, different on every call, as in
     * `reminders/grouped`) is ignored: with it every call would be its own copy that no later call could ever find, and
     * those throw-away copies would push useful ones out of the store.
     */
    private fun cacheKey(request: Request): String = request.url.newBuilder().removeAllQueryParameters("now").build().toString()

    private fun savedResponse(request: Request, saved: StoredResponse, marker: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK (saved copy)")
            .header(marker, "1")
            .body(saved.body.toResponseBody(saved.contentType?.toMediaTypeOrNull()))
            .build()

    /** Like HTTP's only-if-cached: there is nothing saved for this URL, so a cache-first caller gets a 504 and carries on. */
    private fun unsatisfiable(request: Request): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(504)
            .message("Unsatisfiable Request (no saved copy)")
            .body("".toResponseBody(null))
            .build()
}
