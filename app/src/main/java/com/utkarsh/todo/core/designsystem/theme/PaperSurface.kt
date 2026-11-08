package com.utkarsh.todo.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset

/** The texture drawn on top of the paper colour. */
enum class PaperPattern { Plain, Dots, Lines }

private const val DOT_SPACING_DP = 22f
private const val DOT_RADIUS_DP = 0.9f
private const val LINE_SPACING_DP = 30f
private const val LINE_ALPHA = 0.7f

/**
 * Fills the area with the paper colour and, depending on the chosen [PaperPattern], a dot grid or ruled
 * lines in the hairline colour. Every full-screen surface uses this instead of painting a flat colour.
 */
@Composable
fun Modifier.paperSurface(): Modifier {
    val colors = Paper.colors
    val painted = background(colors.paper)
    return when (Paper.pattern) {
        PaperPattern.Plain -> painted
        PaperPattern.Dots -> painted.drawBehind {
            val step = DOT_SPACING_DP * density
            val radius = DOT_RADIUS_DP * density
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    drawCircle(colors.rule, radius, Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        PaperPattern.Lines -> painted.drawBehind {
            val step = LINE_SPACING_DP * density
            var y = step
            while (y < size.height) {
                drawLine(colors.rule.copy(alpha = LINE_ALPHA), Offset(0f, y), Offset(size.width, y), strokeWidth = density)
                y += step
            }
        }
    }
}
