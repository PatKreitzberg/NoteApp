package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.R
import com.wyldsoft.notes.editor.AppMode
import com.wyldsoft.notes.editor.EditorState
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType

private const val TAG = "PenSlotButton"

@DrawableRes
fun penTypeToDrawable(penType: PenType): Int = when (penType) {
    PenType.BALLPEN -> R.drawable.ic_pen_hard
    PenType.FOUNTAIN -> R.drawable.ic_pen_fountain
    PenType.MARKER -> R.drawable.ic_marker_pen
    PenType.PENCIL -> R.drawable.ic_pencil
    PenType.CHARCOAL -> R.drawable.ic_charcoal
    PenType.CHARCOAL_V2 -> R.drawable.ic_charcoal_pen
    PenType.NEO_BRUSH -> R.drawable.ic_brush
    PenType.DASH -> R.drawable.ic_pen_soft
}

private fun Color.contrastColor(): Color {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return if (luminance > 0.5f) Color.Black else Color.White
}

@Composable
fun PenSlotButton(
    slot: Int,
    penProfile: PenProfile,
    isActive: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSettingsExpandedChange: (Boolean) -> Unit
) {
    Log.d(TAG, "PenSlotButton slot=$slot penType=${penProfile.penType} isActive=$isActive")
    val penColor = penProfile.strokeColor
    val iconColor = penColor.contrastColor()

    Box(
        modifier = Modifier
            .size(36.dp)
            .then(
                if (isActive) Modifier.border(3.dp, iconColor, CircleShape)
                else Modifier
            )
            .clip(CircleShape)
            .background(penColor)
            .clickable {
                Log.d(TAG, "slot $slot clicked isActive=$isActive expanded=$expanded")
                if (!isActive) {
                    EditorState.switchToPenSlot(slot)
                    onExpandedChange(false)
                    EditorState.setMode(AppMode.DRAWING)
                } else {
                    if (!expanded) {
                        onExpandedChange(true)
                        onSettingsExpandedChange(false)
                        EditorState.setMode(AppMode.SETTINGS)
                    } else {
                        onExpandedChange(false)
                        EditorState.setMode(AppMode.DRAWING)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(penTypeToDrawable(penProfile.penType)),
            contentDescription = penProfile.penType.displayName,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}
