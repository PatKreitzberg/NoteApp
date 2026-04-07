package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType

private const val TAG = "PenPropertiesPanel"

private val colorSwatches = listOf(
    Color.Black to "Black",
    Color.DarkGray to "Dark Gray",
    Color.Gray to "Gray",
    Color.LightGray to "Light Gray",
    Color.White to "White",
    Color.Red to "Red",
    Color.Blue to "Blue",
    Color.Cyan to "Cyan",
    Color.Green to "Green",
    Color.Magenta to "Magenta",
    Color.Yellow to "Yellow",

    Color(0xFF808000) to "Olive",
    Color(0xFF000080) to "Navy",

    // Additional Modern UI Colors
    Color(0xFF009688) to "Teal",
    Color(0xFFFF9800) to "Orange",
    Color(0xFF3F51B5) to "Indigo",
    Color(0xFF9C27B0) to "Purple",
    Color(0xFF795548) to "Brown",
    Color(0xFFFFC0CB) to "Pink",

    // Soft/Pastel tones for Note Backgrounds
    Color(0xFFFFF9C4) to "Pale Yellow",
    Color(0xFFC8E6C9) to "Mint Green",
    Color(0xFFBBDEFB) to "Sky Blue",
    Color(0xFFF8BBD0) to "Soft Pink",
    Color(0xFFE1BEE7) to "Lavender"
)

@Composable
fun StrokePreview(profile: PenProfile, modifier: Modifier = Modifier) {
    Log.d(TAG, "StrokePreview penType=${profile.penType} width=${profile.strokeWidth}")
    Canvas(
        modifier = modifier
            .border(1.dp, Color.Gray)
            .background(Color(0xFFF5F5F5))
    ) {
        val path = Path()
        val w = size.width
        val h = size.height
        path.moveTo(w * 0.08f, h * 0.5f)
        path.cubicTo(
            w * 0.3f, h * 0.1f,
            w * 0.6f, h * 0.9f,
            w * 0.92f, h * 0.5f
        )
        val previewWidth = profile.strokeWidth.coerceIn(1f, h * 0.6f)
        drawPath(
            path = path,
            color = profile.strokeColor,
            style = Stroke(
                width = previewWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun PenPropertiesPanel(
    currentProfile: PenProfile,
    onProfileChanged: (PenProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* consume click — keep panel open */ },
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Pen Type", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))

            PenType.entries.forEach { type ->
                val isSelected = type == currentProfile.penType
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            Log.d(TAG, "Selected pen type: ${type.displayName}")
                            val newProfile = PenProfile
                                .getDefaultProfile(type, currentProfile.profileId)
                                .copy(strokeColor = currentProfile.strokeColor)
                            onProfileChanged(newProfile)
                        }
                        .padding(vertical = 6.dp, horizontal = 8.dp)
                ) {
                    Text(
                        text = if (isSelected) "● ${type.displayName}" else type.displayName,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                "Width: ${currentProfile.strokeWidth.toInt()}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Slider(
                value = currentProfile.strokeWidth,
                onValueChange = { width ->
                    onProfileChanged(currentProfile.copy(strokeWidth = width))
                },
                onValueChangeFinished = {
                    Log.d(TAG, "Stroke width set to: ${currentProfile.strokeWidth}")
                },
                valueRange = 1f..60f,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Color", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            val rows = colorSwatches.chunked(8)

            Row(modifier = Modifier.fillMaxWidth()) {
                Column {
                    rows.forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { (color, name) ->
                                val isSelected = color == currentProfile.strokeColor
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color.Red else Color.Black
                                        )
                                        .clickable {
                                            Log.d(TAG, "Selected color: $name")
                                            onProfileChanged(currentProfile.copy(strokeColor = color))
                                        }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                StrokePreview(
                    profile = currentProfile,
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                )
            }
        }
    }
}
