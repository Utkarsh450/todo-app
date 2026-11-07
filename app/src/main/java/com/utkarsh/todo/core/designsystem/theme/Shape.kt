package com.utkarsh.todo.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale from the design (4, 6, 8, 10, 14, 20, 28 dp). */
object PaperSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 6.dp
    val sm: Dp = 8.dp
    val md: Dp = 10.dp
    val lg: Dp = 14.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 28.dp
}

/** Corner radii from the design (4, 7, 10, 12, 14, 24 dp). */
object PaperRadius {
    val checkbox: Dp = 4.dp
    val segment: Dp = 7.dp
    val card: Dp = 10.dp
    val toast: Dp = 12.dp
    val fab: Dp = 14.dp
    val sheet: Dp = 24.dp
}

/** Row padding for the cozy / compact density setting. */
enum class Density(val rowPadding: Dp) {
    Cozy(13.dp),
    Compact(8.dp),
}
