package com.wyldsoft.notes.ui.components

import android.graphics.Rect
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.getColorName
import com.wyldsoft.notes.getDefaultStrokeWidthForPenType
import com.wyldsoft.notes.getMaxStrokeSizeForPenType
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.utils.noRippleClickable


@Composable
fun StrokeOptionsPanel(
    viewModel: EditorViewModel,
    currentProfile: PenProfile,
    onProfileChanged: (PenProfile) -> Unit,
    onPanelPositioned: (Rect) -> Unit = {}
) {
    var strokeSize by remember(currentProfile) { mutableStateOf(currentProfile.strokeWidth) }
    var selectedColor by remember(currentProfile) { mutableStateOf(currentProfile.strokeColor) }
    var selectedPenType by remember(currentProfile) { mutableStateOf(currentProfile.penType) }
    val density = LocalDensity.current

    val maxStrokeSize = getMaxStrokeSizeForPenType(selectedPenType)

    // Apply settings immediately when they change
    LaunchedEffect(strokeSize, selectedColor, selectedPenType) {
        val newProfile = currentProfile.copy(
            strokeWidth = strokeSize,
            strokeColor = selectedColor,
            penType = selectedPenType
        )
        onProfileChanged(newProfile)
        viewModel.forceRefresh()
    }

    Column(
        modifier = Modifier
            .wrapContentWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
            .padding(16.dp)
            .onGloballyPositioned { coordinates ->
                val boundingRect = coordinates.boundsInWindow()

                val panelRect = Rect(
                    with(density) { boundingRect.left.toDp().value.toInt() },
                    with(density) { boundingRect.top.toDp().value.toInt() },
                    with(density) { boundingRect.right.toDp().value.toInt() },
                    with(density) { boundingRect.bottom.toDp().value.toInt() }
                )

                Log.d("StrokeOptionsPanel", "Panel positioned: $panelRect")
                onPanelPositioned(panelRect)
            }
    ) {
        // Header with profile info and preview
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(
                text = "Profile ${currentProfile.profileId + 1} Options",
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 16.dp)
            )

            // Stroke preview
            Box(
                modifier = Modifier
                    .size(strokeSize.dp.coerceAtLeast(8.dp))
                    .clip(CircleShape)
                    .background(selectedColor)
                    .border(1.dp, Color.Gray, CircleShape)
            )
        }

        // Pen type selection
        Text(text = "Pen Type:", fontSize = 14.sp, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(PenType.entries.toTypedArray()) { penType ->
                PenTypeButton(
                    penType = penType,
                    isSelected = selectedPenType == penType,
                    onSelect = {
                        selectedPenType = penType
                        // Adjust stroke size to default for new pen type if current size is outside reasonable range
                        val newDefaultWidth = getDefaultStrokeWidthForPenType(penType)
                        val newMaxSize = getMaxStrokeSizeForPenType(penType)
                        if (strokeSize > newMaxSize || strokeSize < 1f) {
                            strokeSize = newDefaultWidth
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stroke size slider
        Text(text = "Stroke Size: ${strokeSize.toInt()}px", fontSize = 14.sp, color = Color.Black)
        Slider(
            value = strokeSize,
            onValueChange = { strokeSize = it },
            valueRange = 1f..maxStrokeSize,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Color selection
        Text(text = "Color:", fontSize = 14.sp, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))

        // Color grid
        val colors = listOf(
            listOf(Color.Black, Color.DarkGray, Color.Gray, Color.LightGray),
            listOf(Color.Red, Color.Blue, Color.Green, Color(0xFF8B4513)),
            listOf(Color(0xFFFF69B4), Color(0xFFFF8C00), Color(0xFF800080), Color(0xFF008080))
        )

        colors.forEach { colorRow ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colorRow.forEach { color ->
                    ColorButton(
                        color = color,
                        isSelected = selectedColor == color,
                        onSelect = { selectedColor = color }
                    )
                }
            }
        }

    }
}

@Composable
fun PenTypeButton(
    penType: PenType,
    isSelected: Boolean = false,
    onSelect: () -> Unit
) {
    PenButton(
        penType = penType,
        isSelected = isSelected,
        onClick = onSelect
    )
}

@Composable
fun ColorButton(
    color: Color,
    isSelected: Boolean = false,
    onSelect: () -> Unit
) {
    val colorName = getColorName(color)
    Box(
        modifier = Modifier
            .size(40.dp)
            .padding(4.dp)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) Color.Black else Color.LightGray,
                shape = CircleShape
            )
            .clip(CircleShape)
            .background(color)
            .semantics {
                contentDescription = if (isSelected) "$colorName, selected" else colorName
            }
            .noRippleClickable(onClick = onSelect)
    )
}