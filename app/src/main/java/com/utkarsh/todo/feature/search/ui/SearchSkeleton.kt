package com.utkarsh.todo.feature.search.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.component.SkeletonCard
import com.utkarsh.todo.core.designsystem.component.SkeletonSurface

/** Search results while they load: one card of task-shaped rows. */
@Composable
fun SearchSkeleton(modifier: Modifier = Modifier) {
    SkeletonSurface("Searching", modifier.fillMaxWidth().padding(horizontal = 14.dp), testTag = "search-skeleton") {
        SkeletonCard(rows = 4)
    }
}
