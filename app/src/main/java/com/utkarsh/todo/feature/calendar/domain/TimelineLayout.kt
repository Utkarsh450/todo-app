package com.utkarsh.todo.feature.calendar.domain

import java.time.Duration
import java.time.Instant

/** A block placed on the day timeline; [lane] / [laneCount] describe its horizontal slot among overlaps. */
data class PlacedBlock<T>(val item: T, val start: Instant, val end: Instant, val lane: Int, val laneCount: Int)

object TimelineLayout {
    /** Tasks have no end time, so they occupy a short block. */
    val MIN_BLOCK: Duration = Duration.ofMinutes(30)

    /**
     * Assigns overlapping blocks to side-by-side lanes. Blocks that (transitively) overlap share a
     * [PlacedBlock.laneCount]; each takes the first lane that is free when it starts.
     */
    fun <T> place(items: List<T>, start: (T) -> Instant, end: (T) -> Instant?): List<PlacedBlock<T>> {
        val sorted = items
            .map { Triple(it, start(it), maxOf(end(it) ?: start(it), start(it).plus(MIN_BLOCK))) }
            .sortedWith(compareBy({ it.second }, { it.third }))

        val result = mutableListOf<PlacedBlock<T>>()
        var cluster = mutableListOf<Triple<T, Instant, Instant>>()
        var laneEnds = mutableListOf<Instant>()
        var lanes = mutableListOf<Int>()
        var clusterEnd: Instant? = null

        fun flush() {
            val count = laneEnds.size
            cluster.forEachIndexed { i, (item, s, e) -> result += PlacedBlock(item, s, e, lanes[i], count) }
            cluster = mutableListOf(); laneEnds = mutableListOf(); lanes = mutableListOf(); clusterEnd = null
        }

        for (entry in sorted) {
            val (_, s, e) = entry
            if (clusterEnd != null && !s.isBefore(clusterEnd)) flush()
            val free = laneEnds.indexOfFirst { !s.isBefore(it) }
            val lane = if (free >= 0) free else laneEnds.size.also { laneEnds.add(e) }
            if (free >= 0) laneEnds[free] = e
            cluster += entry
            lanes += lane
            clusterEnd = maxOf(clusterEnd ?: e, e)
        }
        flush()
        return result
    }
}
