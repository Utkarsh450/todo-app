package com.utkarsh.todo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Three-family type system from the design:
 * Newsreader (serif display), Schibsted Grotesk (UI sans) and IBM Plex Mono (labels/meta).
 * Families fall back to system fonts when the Google Fonts provider is unavailable.
 */
data class PaperTypography(
    val displayLarge: TextStyle = serif(46.sp, tracking = (-0.025).em, lineHeight = 46.sp),
    val display: TextStyle = serif(34.sp, tracking = (-0.02).em),
    val headline: TextStyle = serif(24.sp, tracking = (-0.02).em, lineHeight = 26.sp),
    val title: TextStyle = serif(20.sp, lineHeight = 23.sp),
    val note: TextStyle = serif(19.sp, lineHeight = 26.sp).copy(fontStyle = FontStyle.Italic),
    val body: TextStyle = sans(14.5.sp, lineHeight = 19.sp),
    val bodySmall: TextStyle = sans(13.sp),
    val label: TextStyle = sans(12.sp, FontWeight.Medium),
    val button: TextStyle = sans(13.sp, FontWeight.SemiBold),
    val navLabel: TextStyle = sans(10.5.sp, FontWeight.SemiBold),
    val monoLabel: TextStyle = mono(10.5.sp, FontWeight.Medium, tracking = 0.1.em),
    val monoMeta: TextStyle = mono(11.sp),
)

private fun serif(size: TextUnit, tracking: TextUnit = TextUnit.Unspecified, lineHeight: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = PaperFonts.Serif, fontSize = size, fontWeight = FontWeight.Normal, letterSpacing = tracking, lineHeight = lineHeight)

private fun sans(size: TextUnit, weight: FontWeight = FontWeight.Normal, lineHeight: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = PaperFonts.Sans, fontSize = size, fontWeight = weight, lineHeight = lineHeight)

private fun mono(size: TextUnit, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = PaperFonts.Mono, fontSize = size, fontWeight = weight, letterSpacing = tracking)

internal object PaperFonts {
    val Serif: FontFamily = FontFamily.Serif
    val Sans: FontFamily = FontFamily.SansSerif
    val Mono: FontFamily = FontFamily.Monospace
}

/** Material typography mapped onto the paper styles so stock M3 components stay on-brand. */
internal fun PaperTypography.toMaterial(): Typography = Typography(
    displayLarge = displayLarge,
    headlineMedium = headline,
    titleLarge = title,
    bodyMedium = body,
    bodySmall = bodySmall,
    labelMedium = label,
    labelSmall = monoLabel,
)
