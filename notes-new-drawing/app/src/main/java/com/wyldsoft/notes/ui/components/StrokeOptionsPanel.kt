package com.wyldsoft.notes.ui.components

import android.graphics.Rect
import android.util.Log
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
    var strokeAlpha by remember(currentProfile) { mutableStateOf(currentProfile.strokeColor.alpha) }
    val density = LocalDensity.current

    val maxStrokeSize = getMaxStrokeSizeForPenType(selectedPenType)

    // Apply settings immediately when they change
    LaunchedEffect(strokeSize, selectedColor, selectedPenType, strokeAlpha) {
        val colorWithAlpha = selectedColor.copy(alpha = strokeAlpha)
        val newProfile = currentProfile.copy(
            strokeWidth = strokeSize,
            strokeColor = colorWithAlpha,
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
        // Header with profile info
        Text(
            text = "Profile ${currentProfile.profileId + 1} Type ${currentProfile.penType}",
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Stroke preview - draws a sample curved stroke
        val previewColor = selectedColor.copy(alpha = strokeAlpha)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                .padding(4.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.05f, h * 0.7f)
                    cubicTo(w * 0.25f, h * 0.2f, w * 0.4f, h * 0.8f, w * 0.55f, h * 0.3f)
                    cubicTo(w * 0.7f, h * 0.1f, w * 0.8f, h * 0.6f, w * 0.95f, h * 0.4f)
                }
                drawPath(
                    path = path,
                    color = previewColor,
                    style = Stroke(
                        width = strokeSize,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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

        // Alpha slider
        Text(text = "Opacity: ${(strokeAlpha * 100).toInt()}%", fontSize = 14.sp, color = Color.Black)
        Slider(
            value = strokeAlpha,
            onValueChange = { strokeAlpha = it },
            valueRange = 0.05f..1f,
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