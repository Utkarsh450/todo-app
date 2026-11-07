package com.utkarsh.todo.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalPaperColors = staticCompositionLocalOf { PaperColors() }
private val LocalPaperTypography = staticCompositionLocalOf { PaperTypography() }
private val LocalDensity = staticCompositionLocalOf { Density.Cozy }

/** Accessors for the Paper design tokens: `Paper.colors`, `Paper.type`, `Paper.density`. */
object Paper {
    val colors: PaperColors
        @Composable @ReadOnlyComposable get() = LocalPaperColors.current
    val type: PaperTypography
        @Composable @ReadOnlyComposable get() = LocalPaperTypography.current
    val density: Density
        @Composable @ReadOnlyComposable get() = LocalDensity.current
}

/**
 * Root theme. The design ships a single light "paper" look, so [darkTheme] is deliberately ignored
 * for now; the parameter keeps call sites stable if a dark variant is added later.
 */
@Composable
fun PaperTheme(
    density: Density = Density.Cozy,
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = PaperColors()
    val typography = PaperTypography()
    val scheme = lightColorScheme(
        primary = colors.ink,
        onPrimary = colors.card,
        secondary = colors.accent,
        background = colors.paper,
        onBackground = colors.ink,
        surface = colors.card,
        onSurface = colors.ink,
        surfaceVariant = colors.soft,
        onSurfaceVariant = colors.muted,
        outline = colors.rule,
    )
    CompositionLocalProvider(
        LocalPaperColors provides colors,
        LocalPaperTypography provides typography,
        LocalDensity provides density,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography.toMaterial(), content = content)
    }
}
