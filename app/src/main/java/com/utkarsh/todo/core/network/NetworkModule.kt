package com.utkarsh.todo.core.network

import android.content.Context
import com.utkarsh.todo.BuildConfig
import com.utkarsh.todo.core.offline.FileOfflineStore
import com.utkarsh.todo.core.offline.HealthApi
import com.utkarsh.todo.core.offline.OfflineCacheInterceptor
import com.utkarsh.todo.core.offline.OfflineMonitor
import com.utkarsh.todo.core.offline.OfflineStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import retrofit2.create
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/** Shared JSON configuration: tolerant of new server fields, omits nothing the API requires. */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = false
}

fun buildRetrofit(baseUrl: String, client: OkHttpClient = OkHttpClient()): Retrofit =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun offlineStore(@ApplicationContext context: Context): OfflineStore = FileOfflineStore(File(context.filesDir, "offline-cache"))

    @Provides
    @Singleton
    fun okHttp(store: OfflineStore, monitor: OfflineMonitor): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(OfflineCacheInterceptor(store, monitor))
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    @Provides
    @Singleton
    fun healthApi(retrofit: Retrofit): HealthApi = retrofit.create()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient): Retrofit = buildRetrofit(BuildConfig.API_BASE_URL, client)
}
