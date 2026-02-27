package com.wyldsoft.notes

import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType

// Helper functions for pen profile management
fun getDefaultStrokeWidthForPenType(penType: PenType): Float {
    return PenProfile.getDefaultProfile(penType).strokeWidth
}

fun getMaxStrokeSizeForPenType(penType: PenType): Float {
    return when (penType) {
        PenType.BALLPEN -> 20f
        PenType.FOUNTAIN -> 30f
        PenType.MARKER -> 60f
        PenType.PENCIL -> 15f
        PenType.CHARCOAL -> 40f
        PenType.CHARCOAL_V2 -> 40f
        PenType.NEO_BRUSH -> 50f
        PenType.DASH -> 25f
    }
}

fun getColorName(color: androidx.compose.ui.graphics.Color): String {
    return when (color) {
        androidx.compose.ui.graphics.Color.Black -> "Black"
        androidx.compose.ui.graphics.Color.DarkGray -> "Dark Gray"
        androidx.compose.ui.graphics.Color.Gray -> "Gray"
        androidx.compose.ui.graphics.Color.LightGray -> "Light Gray"
        androidx.compose.ui.graphics.Color.Red -> "Red"
        androidx.compose.ui.graphics.Color.Blue -> "Blue"
        androidx.compose.ui.graphics.Color.Green -> "Green"
        androidx.compose.ui.graphics.Color(0xFF8B4513) -> "Brown"
        androidx.compose.ui.graphics.Color(0xFFFF69B4) -> "Pink"
        androidx.compose.ui.graphics.Color(0xFFFF8C00) -> "Orange"
        androidx.compose.ui.graphics.Color(0xFF800080) -> "Purple"
        androidx.compose.ui.graphics.Color(0xFF008080) -> "Teal"
        else -> "Custom"
    }
}