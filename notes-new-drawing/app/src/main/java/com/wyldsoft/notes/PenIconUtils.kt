package com.wyldsoft.notes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.R.drawable

object PenIconUtils {
    @Composable
    fun getIconForPenType(penType: PenType): ImageVector {
        return when (penType) { //         imageVector = ImageVector.vectorResource(id = R.drawable.your_vector_drawable_name),
            PenType.BALLPEN     -> ImageVector.vectorResource(id = drawable.ic_pen_hard) // Ballpoint pen
            PenType.FOUNTAIN    -> ImageVector.vectorResource(id = drawable.ic_pen_fountain) // Fountain pen
            PenType.MARKER      -> ImageVector.vectorResource(id = drawable.ic_pen_soft) // Marker/highlighter
            PenType.PENCIL      -> ImageVector.vectorResource(id = drawable.ic_pencil) // Pencil
            PenType.CHARCOAL    -> ImageVector.vectorResource(id = drawable.ic_charcoal_pen) // Charcoal
            PenType.CHARCOAL_V2 -> ImageVector.vectorResource(id = drawable.ic_charcoal) // Charcoal V2
            PenType.NEO_BRUSH   -> ImageVector.vectorResource(id = drawable.ic_marker_pen) // Neo brush
            PenType.DASH -> Icons.Default.Timeline // Dash pen
        } as ImageVector
    }

    fun getContentDescriptionForPenType(penType: PenType): String {
        return when (penType) {
            PenType.BALLPEN     -> "Ballpoint Pen"
            PenType.FOUNTAIN    -> "Fountain Pen"
            PenType.MARKER      -> "Marker"
            PenType.PENCIL      -> "Pencil"
            PenType.CHARCOAL    -> "Charcoal"
            PenType.CHARCOAL_V2 -> "Charcoal V2"
            PenType.NEO_BRUSH   -> "Neo Brush"
            PenType.DASH        -> "Dash Pen"
        }
    }
}