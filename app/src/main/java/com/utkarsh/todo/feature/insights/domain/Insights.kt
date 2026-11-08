package com.utkarsh.todo.feature.insights.domain

import java.time.LocalDate

enum class HeatmapMode { TASKS, HABITS }

data class HeatmapDay(val date: LocalDate, val count: Int)

data class HeatmapSummary(val total: Int, val streak: Int, val best: Int, val rhythm: List<Double>)

data class Heatmap(val mode: HeatmapMode, val days: List<HeatmapDay>, val summary: HeatmapSummary)

interface InsightsRepository {
    /** Counts per day in [from, to] inclusive; [tzOffsetMinutes] is the device zone's offset from UTC. */
    suspend fun heatmap(mode: HeatmapMode, from: LocalDate, to: LocalDate, tzOffsetMinutes: Int): Result<Heatmap>
}

/** Heat intensity 0..4 for a day count; tasks use bands, habits step per habit kept (design values). */
fun heatLevel(mode: HeatmapMode, count: Int): Int = when (mode) {
    HeatmapMode.TASKS -> when {
        count <= 0 -> 0
        count < 3 -> 1
        count < 5 -> 2
        count < 7 -> 3
        else -> 4
    }
    HeatmapMode.HABITS -> if (count <= 0) 0 else minOf(count, 3) + 1
}
