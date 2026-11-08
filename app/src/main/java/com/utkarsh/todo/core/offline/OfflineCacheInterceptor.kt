package com.utkarsh.todo.core.offline

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
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
        val request = chain.request()
        if (request.method != "GET" || request.url.encodedPath.endsWith("/health")) return chain.proceed(request)
        val key = request.url.toString()
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
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK (saved copy)")
                .header(OFFLINE_CACHE_HEADER, "1")
                .body(saved.body.toResponseBody(saved.contentType?.toMediaTypeOrNull()))
                .build()
        }
    }
}
