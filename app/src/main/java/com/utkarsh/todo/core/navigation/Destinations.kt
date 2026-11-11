package com.utkarsh.todo.core.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
sealed interface Destination {
    @Serializable data object Today : Destination
    @Serializable data object Calendar : Destination
    @Serializable data object DueSoon : Destination
    @Serializable data object Insights : Destination
    @Serializable data object Customize : Destination
    @Serializable data object Assistant : Destination
    @Serializable data object BrainDump : Destination
    @Serializable data object Projects : Destination
    @Serializable data object Search : Destination
    @Serializable data class TaskDetail(val taskId: String) : Destination
    @Serializable data class Focus(val taskId: String) : Destination
}
