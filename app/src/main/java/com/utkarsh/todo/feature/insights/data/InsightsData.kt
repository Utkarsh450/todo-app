package com.utkarsh.todo.feature.insights.data

import com.utkarsh.todo.core.common.resultOf
import com.utkarsh.todo.feature.insights.domain.Heatmap
import com.utkarsh.todo.feature.insights.domain.HeatmapDay
import com.utkarsh.todo.feature.insights.domain.HeatmapSummary
import com.utkarsh.todo.feature.insights.domain.InsightsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable data class DayCountDto(val date: String, val count: Int)

@Serializable data class SummaryDto(val total: Int, val streak: Int, val best: Int, val rhythm: List<Double>)

@Serializable data class HeatmapDto(val mode: String, val days: List<DayCountDto>, val summary: SummaryDto)

interface InsightsApi {
    @GET("insights/heatmap?mode=tasks")
    suspend fun heatmap(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("tzOffsetMinutes") tzOffsetMinutes: Int,
    ): HeatmapDto
}

class InsightsRepositoryImpl @Inject constructor(private val api: InsightsApi) : InsightsRepository {
    override suspend fun heatmap(from: LocalDate, to: LocalDate, tzOffsetMinutes: Int) = resultOf {
        val dto = api.heatmap(from.toString(), to.toString(), tzOffsetMinutes)
        Heatmap(
            days = dto.days.map { HeatmapDay(LocalDate.parse(it.date), it.count) },
            summary = HeatmapSummary(dto.summary.total, dto.summary.streak, dto.summary.best, dto.summary.rhythm),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class InsightsBindings {
    @Binds
    abstract fun repository(impl: InsightsRepositoryImpl): InsightsRepository

    companion object {
        @Provides
        @Singleton
        fun api(retrofit: Retrofit): InsightsApi = retrofit.create()
    }
}
