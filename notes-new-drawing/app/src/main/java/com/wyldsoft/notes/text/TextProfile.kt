package com.wyldsoft.notes.text

import androidx.compose.ui.graphics.Color

/**
 * Immutable snapshot of text rendering properties for the TEXT mode tool.
 * fontSize is in pixels (sp-equivalent at 1:1 density for now).
 * fontFamily maps to Android Typeface: "sans-serif", "serif", or "monospace".
 * color is a Compose Color; convert via .toArgb() when writing to Shape.strokeColor.
 */
data class TextProfile(
    val fontSize: Float = 32f,
    val fontFamily: String = "sans-serif",
    val color: Color = Color.Black
)
