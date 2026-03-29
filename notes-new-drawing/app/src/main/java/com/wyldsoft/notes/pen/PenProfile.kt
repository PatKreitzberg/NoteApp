package com.wyldsoft.notes.pen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Immutable snapshot of pen settings: stroke width, color, and pen type.
 * Used by BaseDrawingActivity to configure the Paint and by OnyxDrawingActivity
 * to set the Onyx TouchHelper stroke style. Also passed to ShapeFactory when
 * creating new Shape instances so each shape records the pen state at draw time.
 *
 * getOnyxStrokeStyleInternal() maps PenType to the integer constant that
 * TouchHelper.setStrokeStyle() expects.
 */
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
            PenType.PENCIL -> 0
            PenType.BALLPEN -> 0
            PenType.FOUNTAIN -> 1
            PenType.MARKER -> 2
            PenType.NEO_BRUSH -> 3
            PenType.CHARCOAL -> 4
            PenType.CHARCOAL_V2 -> 6
            PenType.DASH -> 5
        }
    }
}