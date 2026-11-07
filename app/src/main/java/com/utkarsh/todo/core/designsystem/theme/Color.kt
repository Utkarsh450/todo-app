package com.utkarsh.todo.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Raw palette of the "Paper System" design. Use [PaperColors] through the theme, not these directly. */
internal object PaperPalette {
    val Paper = Color(0xFFF4F0E7)
    val Card = Color(0xFFFFFDF8)
    val Rule = Color(0xFFE3DCCD)
    val Soft = Color(0xFFEAE4D7)
    val Ink = Color(0xFF2B2824)
    val Muted = Color(0xFF8A8377)
    val MutedSoft = Color(0xFFA39B8D)
    val Off = Color(0xFFDCD5C6)
    val Dashed = Color(0xFFCFC7B6)
    val Vermilion = Color(0xFFC4532F)
    val VermilionLight = Color(0xFFF28058)
    val Archive = Color(0xFF6E675C)
}

/** Semantic colour roles exposed to the UI. */
data class PaperColors(
    val paper: Color = PaperPalette.Paper,
    val card: Color = PaperPalette.Card,
    val rule: Color = PaperPalette.Rule,
    val soft: Color = PaperPalette.Soft,
    val ink: Color = PaperPalette.Ink,
    val muted: Color = PaperPalette.Muted,
    val mutedSoft: Color = PaperPalette.MutedSoft,
    val switchOff: Color = PaperPalette.Off,
    val dashed: Color = PaperPalette.Dashed,
    val accent: Color = PaperPalette.Vermilion,
    val accentLight: Color = PaperPalette.VermilionLight,
    val archive: Color = PaperPalette.Archive,
    /** Heat-map ramps: tasks use the accent ramp, habits use the ink ramp. */
    val taskHeat: List<Color> = listOf(
        PaperPalette.Soft, Color(0xFFF4C5B1), Color(0xFFED997B), Color(0xFFDA694A), Color(0xFFA83825),
    ),
    val habitHeat: List<Color> = listOf(
        PaperPalette.Soft, Color(0xFFCFC6B3), Color(0xFF9C9382), Color(0xFF5E574D), PaperPalette.Ink,
    ),
)

/** Project label colours (design: Design, Engineering, Learning, Personal, Work). */
object ProjectColors {
    val Design = Color(0xFF935EDF)
    val Engineering = Color(0xFF2784D5)
    val Learning = Color(0xFFDF911A)
    val Personal = Color(0xFF2FA465)
    val Work = Color(0xFFE05D3D)
}

/** Priority colours (design: Urgent, High, Normal, Low). */
object PriorityColors {
    val Urgent = Color(0xFFE23439)
    val High = Color(0xFFE58212)
    val Normal = Color(0xFF3A84CA)
    val Low = Color(0xFFA3A3A0)
}

/** Quick-add token chip colours (background / foreground pairs). */
object TokenColors {
    val DayBg = Color(0xFFDDF1FF)
    val DayFg = Color(0xFF00569F)
    val ProjectBg = Color(0xFFF2E9FF)
    val ProjectFg = Color(0xFF6635A3)
    val PriorityBg = Color(0xFFFFE6DB)
    val PriorityFg = Color(0xFFAF2F09)
    val RepeatBg = Color(0xFFDAF7E3)
    val RepeatFg = Color(0xFF005E31)
}
