package com.utkarsh.todo.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Line icons ported from the design's SVGs (20x20 viewBox, 1.5 stroke). Stroke colour is a placeholder:
 * `Icon(tint = ...)` recolours them.
 */
object PaperIcons {
    val Today: ImageVector by lazy {
        icon("Today") {
            strokePath(1.5f) { roundedRect(3.5f, 3.5f, 13f, 13f, 2.5f) }
            strokePath(1.5f, round = true) { moveTo(7f, 10f); lineTo(9f, 12f); lineTo(13f, 8f) }
        }
    }

    val Calendar: ImageVector by lazy {
        icon("Calendar") {
            strokePath(1.5f) { roundedRect(3f, 4.5f, 14f, 12f, 2.5f) }
            strokePath(1.5f) {
                moveTo(3f, 8.5f); horizontalLineTo(17f)
                moveTo(7f, 3f); verticalLineTo(6f)
                moveTo(13f, 3f); verticalLineTo(6f)
            }
        }
    }

    val Reminders: ImageVector by lazy {
        icon("Reminders") {
            strokePath(1.5f, round = true) {
                moveTo(10f, 3.5f)
                arcTo(4.5f, 4.5f, 0f, false, true, 14.5f, 8f)
                verticalLineTo(11.5f)
                lineTo(16f, 14f)
                horizontalLineTo(4f)
                lineTo(5.5f, 11.5f)
                verticalLineTo(8f)
                arcTo(4.5f, 4.5f, 0f, false, true, 10f, 3.5f)
                close()
                moveTo(8.3f, 16.5f); horizontalLineTo(11.7f)
            }
        }
    }

    /** Folder: the Projects tab. */
    val Projects: ImageVector by lazy {
        icon("Projects") {
            strokePath(1.5f, round = true) {
                moveTo(3f, 5.5f); horizontalLineTo(8f); lineTo(9.5f, 7.5f); horizontalLineTo(17f)
                verticalLineTo(15.5f); horizontalLineTo(3f); close()
            }
        }
    }

    /** Microphone: voice input. */
    val Mic: ImageVector by lazy {
        icon("Mic") {
            strokePath(1.5f) { roundedRect(7.5f, 2.5f, 5f, 9f, 2.5f) }
            strokePath(1.5f, round = true) {
                moveTo(4.5f, 9.5f)
                arcTo(5.5f, 5.5f, 0f, false, false, 15.5f, 9.5f)
                moveTo(10f, 15f); verticalLineTo(17.5f)
            }
        }
    }

    /** Two sliders: the "customize" entry point. */
    val Customize: ImageVector by lazy {
        icon("Customize") {
            strokePath(1.5f, round = true) {
                moveTo(3f, 6f); horizontalLineTo(17f)
                moveTo(3f, 14f); horizontalLineTo(17f)
            }
            strokePath(1.5f) { circle(7f, 6f, 2f) }
            strokePath(1.5f) { circle(13f, 14f, 2f) }
        }
    }

    /** Stopwatch: the Timer tab. */
    val Timer: ImageVector by lazy {
        icon("Timer") {
            strokePath(1.5f) { circle(10f, 11.5f, 5.5f) }
            strokePath(1.5f, round = true) {
                moveTo(8f, 2.5f); horizontalLineTo(12f)
                moveTo(10f, 2.5f); verticalLineTo(6f)
                moveTo(10f, 11.5f); lineTo(12.3f, 9.2f)
            }
        }
    }

    val Insights: ImageVector by lazy {
        icon("Insights") {
            strokePath(1.7f, round = true) {
                moveTo(4f, 16f); verticalLineTo(9f)
                moveTo(8f, 16f); verticalLineTo(5f)
                moveTo(12f, 16f); verticalLineTo(11f)
                moveTo(16f, 16f); verticalLineTo(7f)
            }
        }
    }
}

private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 20f, viewportHeight = 20f).apply(build).build()

private fun ImageVector.Builder.strokePath(width: Float, round: Boolean = false, block: PathBuilder.() -> Unit) {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = if (round) StrokeCap.Round else StrokeCap.Butt,
        strokeLineJoin = if (round) StrokeJoin.Round else StrokeJoin.Miter,
        pathBuilder = block,
    )
}

private fun PathBuilder.roundedRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    horizontalLineTo(x + w - r)
    arcTo(r, r, 0f, false, true, x + w, y + r)
    verticalLineTo(y + h - r)
    arcTo(r, r, 0f, false, true, x + w - r, y + h)
    horizontalLineTo(x + r)
    arcTo(r, r, 0f, false, true, x, y + h - r)
    verticalLineTo(y + r)
    arcTo(r, r, 0f, false, true, x + r, y)
    close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    close()
}
