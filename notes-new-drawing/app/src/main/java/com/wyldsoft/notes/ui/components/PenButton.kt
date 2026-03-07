package com.wyldsoft.notes.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.PenIconUtils
import com.wyldsoft.notes.pen.PenType

@Composable
fun PenButton(
    penType: PenType,
    isSelected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color = Color.Black,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp
) {
    SelectableIconButton(
        icon = PenIconUtils.getIconForPenType(penType),
        contentDescription = PenIconUtils.getContentDescriptionForPenType(penType),
        isSelected = isSelected,
        onClick = onClick,
        selectedColor = selectedColor,
        size = size,
        iconSize = iconSize
    )
}
