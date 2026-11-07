package com.utkarsh.todo.core.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
sealed interface Destination {
    @Serializable data object Today : Destination
    @Serializable data object Calendar : Destination
    @Serializable data object Reminders : Destination
    @Serializable data object Insights : Destination
    @Serializable data class TaskDetail(val taskId: String) : Destination
}
