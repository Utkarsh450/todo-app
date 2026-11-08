package com.utkarsh.todo.core.offline

import retrofit2.http.GET

interface HealthApi {
    @GET("health")
    suspend fun health()
}
