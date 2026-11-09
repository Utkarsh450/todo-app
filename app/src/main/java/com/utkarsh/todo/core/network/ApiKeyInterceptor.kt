package com.utkarsh.todo.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the `X-Api-Key` header to every request when the app was built with a key (`-PapiKey=...`), which is what a hosted
 * backend expects. With no key (the local backend) requests go out unchanged.
 */
class ApiKeyInterceptor(private val key: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (key.isBlank()) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(HEADER, key).build())
    }

    companion object {
        const val HEADER = "X-Api-Key"
    }
}
