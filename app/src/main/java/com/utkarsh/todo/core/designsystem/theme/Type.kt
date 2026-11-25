package com.utkarsh.todo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.utkarsh.todo.R

/**
 * One-family type system: Satoshi everywhere (display, UI text and the small uppercase labels), so the whole app reads as
 * one voice. The roles keep their names (serif / sans / mono helpers) so every screen still asks for "display" or
 * "monoLabel"; they only differ in size, weight and letter spacing now. The font files are bundled in res/font, so the
 * app looks the same offline.
 */
data class PaperTypography(
    val displayLarge: TextStyle = display(46.sp, tracking = (-0.03).em, lineHeight = 48.sp),
    val display: TextStyle = display(34.sp, tracking = (-0.025).em),
    val headline: TextStyle = display(24.sp, tracking = (-0.02).em, lineHeight = 28.sp),
    val title: TextStyle = display(20.sp, lineHeight = 25.sp),
    val note: TextStyle = display(19.sp, lineHeight = 26.sp).copy(fontStyle = FontStyle.Italic),
    val body: TextStyle = sans(14.5.sp, lineHeight = 19.sp),
    val bodySmall: TextStyle = sans(13.sp),
    val label: TextStyle = sans(12.sp, FontWeight.Medium),
    val button: TextStyle = sans(13.sp, FontWeight.SemiBold),
    val navLabel: TextStyle = sans(10.5.sp, FontWeight.SemiBold),
    val monoLabel: TextStyle = sans(10.5.sp, FontWeight.Bold, tracking = 0.1.em),
    val monoMeta: TextStyle = sans(11.sp, FontWeight.Medium),
)

/** Big headings: Satoshi Medium, so they keep some weight at large sizes. */
private fun display(size: TextUnit, tracking: TextUnit = TextUnit.Unspecified, lineHeight: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = PaperFonts.Satoshi, fontSize = size, fontWeight = FontWeight.Medium, letterSpacing = tracking, lineHeight = lineHeight)

private fun sans(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = TextUnit.Unspecified,
    tracking: TextUnit = TextUnit.Unspecified,
) = TextStyle(fontFamily = PaperFonts.Satoshi, fontSize = size, fontWeight = weight, lineHeight = lineHeight, letterSpacing = tracking)

/**
 * The bundled font: Satoshi by Indian Type Foundry (Fontshare Free Font licence, text in assets/licenses: free for
 * commercial use and for embedding in our own app, but the files must not be passed on to others). Both files are variable
 * fonts, so one file serves every weight.
 */
@OptIn(ExperimentalTextApi::class)
internal object PaperFonts {
    private fun variable(resId: Int, weight: FontWeight, style: FontStyle = FontStyle.Normal) =
        Font(resId, weight, style, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

    val Satoshi: FontFamily = FontFamily(
        variable(R.font.satoshi, FontWeight.Normal),
        variable(R.font.satoshi, FontWeight.Medium),
        variable(R.font.satoshi, FontWeight.SemiBold),
        variable(R.font.satoshi, FontWeight.Bold),
        variable(R.font.satoshi_italic, FontWeight.Normal, FontStyle.Italic),
        variable(R.font.satoshi_italic, FontWeight.Medium, FontStyle.Italic),
        variable(R.font.satoshi_italic, FontWeight.SemiBold, FontStyle.Italic),
    )
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
