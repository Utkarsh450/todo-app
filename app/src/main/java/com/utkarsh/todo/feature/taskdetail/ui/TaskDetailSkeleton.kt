package com.utkarsh.todo.feature.taskdetail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.component.SkeletonBlock
import com.utkarsh.todo.core.designsystem.component.SkeletonLine
import com.utkarsh.todo.core.designsystem.component.SkeletonRow
import com.utkarsh.todo.core.designsystem.component.SkeletonSurface

/** The task page while it loads: status, a two-line title, three meta rows and a few subtasks. */
@Composable
fun TaskDetailSkeleton(modifier: Modifier = Modifier) {
    SkeletonSurface("Loading the task", modifier.fillMaxWidth().padding(horizontal = 20.dp), testTag = "detail-skeleton") {
        SkeletonBlock(Modifier.width(84.dp).height(10.dp), RoundedCornerShape(50))
        Column(Modifier.padding(top = 14.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonLine(0.9f, height = 30.dp)
            SkeletonLine(0.55f, height = 30.dp)
        }
        repeat(3) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.width(54.dp).height(10.dp), RoundedCornerShape(50))
                SkeletonBlock(Modifier.padding(start = 56.dp).width(120.dp).height(13.dp), RoundedCornerShape(50))
            }
        }
        SkeletonBlock(Modifier.padding(top = 22.dp, bottom = 6.dp).width(70.dp).height(10.dp), RoundedCornerShape(50))
        repeat(3) { SkeletonRow(Modifier.padding(horizontal = 0.dp), titleWidth = if (it == 1) 0.5f else 0.7f, metaWidth = null) }
    }
}
