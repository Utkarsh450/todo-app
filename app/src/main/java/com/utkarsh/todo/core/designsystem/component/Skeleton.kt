package com.utkarsh.todo.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.theme.Paper

/** Sweep position of the shimmer, 0..1, shared by every block inside one [SkeletonSurface] so they move together. */
private val LocalShimmer = compositionLocalOf<State<Float>> { StaticShimmer }
private val StaticShimmer: State<Float> = mutableFloatStateOf(0.5f)

/** True when the person turned animations off in system settings ("Remove animations"); skeletons then sit still. */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }.getOrDefault(true)
    }
}

/**
 * Wraps placeholder blocks that stand in for content that is still loading. One soft light sweeps across all of
 * them, and the whole thing is announced as a single "[label]" instead of a pile of empty shapes.
 */
@Composable
fun SkeletonSurface(
    label: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shimmer: State<Float> = if (rememberAnimationsEnabled()) {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing), RepeatMode.Restart),
            label = "skeletonSweep",
        )
    } else {
        StaticShimmer
    }
    CompositionLocalProvider(LocalShimmer provides shimmer) {
        Column(
            modifier.let { if (testTag != null) it.testTag(testTag) else it }.clearAndSetSemantics { contentDescription = label },
            content = content,
        )
    }
}

/**
 * A grey-paper placeholder shape with a light sweeping over it. [ai] gives the sweep a warm tint, used where the
 * assistant is writing an answer, so those placeholders read as "being written" rather than "being fetched".
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    ai: Boolean = false,
) {
    val colors = Paper.colors
    val sweep = LocalShimmer.current
    val base = colors.soft
    val light = if (ai) colors.accentLight.copy(alpha = 0.38f) else colors.card
    Box(
        modifier.clip(shape).drawBehind {
            val band = size.width * 0.7f
            val start = -band + (size.width + band) * sweep.value
            drawRect(Brush.linearGradient(listOf(base, light, base), start = Offset(start, 0f), end = Offset(start + band, size.height * 0.4f)))
        },
    )
}

/** A pill-shaped line of text, as wide as [width] of its parent (0..1). */
@Composable
fun SkeletonLine(width: Float, modifier: Modifier = Modifier, height: Dp = 12.dp, ai: Boolean = false) {
    SkeletonBlock(modifier.fillMaxWidth(width).height(height), RoundedCornerShape(50), ai)
}

/** A paragraph of placeholder lines getting shorter, like an answer being written. */
@Composable
fun SkeletonParagraph(modifier: Modifier = Modifier, widths: List<Float> = listOf(1f, 0.92f, 0.6f), ai: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        widths.forEach { SkeletonLine(it, ai = ai, height = 13.dp) }
    }
}

/** A round marker (checkbox, dot) followed by one or two lines: the shape of a list row. */
@Composable
fun SkeletonRow(
    modifier: Modifier = Modifier,
    titleWidth: Float = 0.7f,
    metaWidth: Float? = 0.35f,
    leading: Dp = 18.dp,
    ai: Boolean = false,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBlock(Modifier.size(leading), CircleShape, ai)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(titleWidth, ai = ai, height = 13.dp)
            if (metaWidth != null) SkeletonLine(metaWidth, ai = ai, height = 9.dp)
        }
    }
}

/** A card-shaped placeholder holding [rows] list rows. */
@Composable
fun SkeletonCard(rows: Int, modifier: Modifier = Modifier, ai: Boolean = false) {
    PaperCard(modifier.fillMaxWidth()) {
        repeat(rows) { index ->
            if (index > 0) androidx.compose.material3.HorizontalDivider(color = Paper.colors.rule)
            SkeletonRow(titleWidth = if (index % 2 == 0) 0.72f else 0.55f, metaWidth = if (index % 3 == 1) null else 0.34f, ai = ai)
        }
    }
}
