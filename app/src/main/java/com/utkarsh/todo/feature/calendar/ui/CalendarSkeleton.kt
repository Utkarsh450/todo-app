package com.utkarsh.todo.feature.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.component.SkeletonBlock
import com.utkarsh.todo.core.designsystem.component.SkeletonCard
import com.utkarsh.todo.core.designsystem.component.SkeletonSurface
import com.utkarsh.todo.core.designsystem.component.SkeletonLine

/** The calendar while it loads: a month grid of round day marks and a short agenda. */
@Composable
fun CalendarSkeleton(modifier: Modifier = Modifier) {
    SkeletonSurface("Loading the calendar", modifier.fillMaxWidth().padding(horizontal = 14.dp), testTag = "calendar-skeleton") {
        PaperCard(Modifier.fillMaxWidth()) {
            repeat(6) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    repeat(7) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            if (row == 0) SkeletonBlock(Modifier.size(width = 22.dp, height = 8.dp), RoundedCornerShape(50))
                            else SkeletonBlock(Modifier.size(26.dp), CircleShape)
                        }
                    }
                }
            }
        }
        SkeletonLine(0.4f, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp), height = 16.dp)
        SkeletonCard(rows = 2)
        Box(Modifier.height(12.dp))
    }
}
