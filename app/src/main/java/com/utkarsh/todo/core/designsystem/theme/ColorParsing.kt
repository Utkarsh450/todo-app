package com.utkarsh.todo.core.designsystem.theme

import androidx.compose.ui.graphics.Color

private val HEX = Regex("^#[0-9A-Fa-f]{6}$")

/** Parses `#RRGGBB` (as sent by the API) into an opaque [Color]; falls back to [fallback] if malformed. */
fun parseHexColor(hex: String, fallback: Color = Color.Gray): Color =
    if (HEX.matches(hex)) Color(0xFF000000 or hex.substring(1).toLong(16)) else fallback
