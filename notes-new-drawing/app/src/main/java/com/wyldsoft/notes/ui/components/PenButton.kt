package com.wyldsoft.notes.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) selectedColor else Color.Transparent,
            contentColor = if (isSelected) Color.White else Color.Black
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color.Black else Color.Gray
        ),
        modifier = Modifier.size(size),
        contentPadding = PaddingValues(4.dp)
    ) {
        Icon(
            imageVector = PenIconUtils.getIconForPenType(penType),
            contentDescription = PenIconUtils.getContentDescriptionForPenType(penType),
            modifier = Modifier.size(iconSize)
        )
    }
}
