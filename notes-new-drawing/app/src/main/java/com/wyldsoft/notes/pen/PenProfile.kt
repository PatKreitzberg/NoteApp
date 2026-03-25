package com.wyldsoft.notes.pen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class PenProfile(
    val strokeWidth: Float,
    var penType: PenType, // Made mutable to allow pen type switching
    val strokeColor: Color,
    val profileId: Int = 0 // Added profile ID for identification
) {
    companion object {
        fun getDefaultProfile(penType: PenType, profileId: Int = 0): PenProfile {
            val defaultStrokeWidth = when (penType) {
                PenType.BALLPEN -> 5f
                PenType.FOUNTAIN -> 8f
                PenType.MARKER -> 20f
                PenType.PENCIL -> 3f
                PenType.CHARCOAL -> 15f
                PenType.CHARCOAL_V2 -> 15f
                PenType.NEO_BRUSH -> 25f
                PenType.DASH -> 6f
            }

            return PenProfile(
                strokeWidth = defaultStrokeWidth,
                penType = penType,
                strokeColor = Color.Black,
                profileId = profileId
            )
        }
    }

    fun getColorAsInt(): Int = strokeColor.toArgb()

    internal fun getOnyxStrokeStyleInternal(): Int {
        return when (penType) {
            PenType.BALLPEN -> 0
            PenType.FOUNTAIN -> 1
            PenType.MARKER -> 2
            PenType.PENCIL -> 3
            PenType.CHARCOAL -> 4
            PenType.CHARCOAL_V2 -> 5
            PenType.NEO_BRUSH -> 6
            PenType.DASH -> 7
        }
    }
}