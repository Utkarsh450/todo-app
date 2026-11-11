package com.utkarsh.todo.feature.insights.domain

import java.time.LocalDate

data class HeatmapDay(val date: LocalDate, val count: Int)

data class HeatmapSummary(val total: Int, val streak: Int, val best: Int, val rhythm: List<Double>)

data class Heatmap(val days: List<HeatmapDay>, val summary: HeatmapSummary)

interface InsightsRepository {
    /** Counts per day in [from, to] inclusive; [tzOffsetMinutes] is the device zone's offset from UTC. */
    suspend fun heatmap(from: LocalDate, to: LocalDate, tzOffsetMinutes: Int): Result<Heatmap>
}

/** Heat intensity 0..4 for the number of tasks finished on a day (design values). */
fun heatLevel(count: Int): Int = when {
    count <= 0 -> 0
    count < 3 -> 1
    count < 5 -> 2
    count < 7 -> 3
    else -> 4
}
