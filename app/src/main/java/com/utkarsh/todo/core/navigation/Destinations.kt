package com.utkarsh.todo.core.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
sealed interface Destination {
    @Serializable data object Today : Destination
    @Serializable data object Calendar : Destination
    @Serializable data object DueSoon : Destination
    @Serializable data object Insights : Destination
    /** The Timer tab: a focus countdown that is not tied to a task (the one on a task page is [Focus]). */
    @Serializable data object Timer : Destination
    @Serializable data object Customize : Destination
    @Serializable data object Assistant : Destination
    /** What the assistant remembers about the owner (opened from the assistant). */
    @Serializable data object Memory : Destination
    @Serializable data object BrainDump : Destination
    @Serializable data object Projects : Destination
    @Serializable data object Search : Destination
    /** Screen 15. [startEpochSeconds] > 0 prefills the day and time (a tap on a time slot), [epochDay] >= 0 just the day (a tap on a date), [eventId] edits that event. */
    @Serializable data class Create(val type: String = "TASK", val startEpochSeconds: Long = 0L, val epochDay: Long = -1L, val eventId: String = "") : Destination
    @Serializable data class TaskDetail(val taskId: String) : Destination
    @Serializable data class Focus(val taskId: String) : Destination
}
