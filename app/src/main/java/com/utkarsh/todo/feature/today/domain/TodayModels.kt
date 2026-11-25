package com.utkarsh.todo.feature.today.domain

import com.utkarsh.todo.feature.tasks.domain.Priority

/** Display model of a task row on the Today screen. */
data class TaskItem(
    val id: String,
    val title: String,
    val timeLabel: String,
    val done: Boolean,
    val priority: Priority,
    val pinned: Boolean,
    val projectName: String?,
    val projectColorHex: String?,
    val subtasksDone: Int,
    val subtasksTotal: Int,
)

/** Shown in the undo snackbar after a swipe action. */
data class UndoPrompt(val message: String)
