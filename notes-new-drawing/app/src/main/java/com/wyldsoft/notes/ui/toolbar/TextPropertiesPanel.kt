package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.text.TextProfile

private const val TAG = "TextPropertiesPanel"

private val fontOptions = listOf(
    "sans-serif" to "Sans-serif",
    "serif" to "Serif",
    "monospace" to "Monospace"
)

private val sizePresets = listOf(16, 24, 32, 48, 64, 96)

private fun fontFamilyFor(name: String): FontFamily = when (name) {
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    else -> FontFamily.SansSerif
}

@Composable
fun TextPropertiesPanel(
    textProfile: TextProfile,
    onProfileChanged: (TextProfile) -> Unit,
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
            // ── Font ────────────────────────────────────────────────────────
            Text("Font", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))

            fontOptions.forEach { (key, label) ->
                val isSelected = textProfile.fontFamily == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            Log.d(TAG, "Selected font: $key")
                            onProfileChanged(textProfile.copy(fontFamily = key))
                        }
                        .padding(vertical = 6.dp, horizontal = 8.dp)
                ) {
                    Text(
                        text = if (isSelected) "● $label" else label,
                        fontFamily = fontFamilyFor(key),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Size ────────────────────────────────────────────────────────
            Text(
                "Size: ${textProfile.fontSize.toInt()}pt",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sizePresets.forEach { size ->
                    val isSelected = textProfile.fontSize == size.toFloat()
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .background(if (isSelected) Color.LightGray else Color.White)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = Color.Black
                            )
                            .clickable {
                                Log.d(TAG, "Selected size: $size")
                                onProfileChanged(textProfile.copy(fontSize = size.toFloat()))
                            }
                    ) {
                        Text(text = "$size", fontSize = 11.sp)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Color ───────────────────────────────────────────────────────
            Text("Color", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            // Preview
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, Color.Gray)
                    .background(Color(0xFFF5F5F5))
                    .padding(8.dp)
            ) {
                Text(
                    text = "Sample",
                    color = textProfile.color,
                    fontSize = textProfile.fontSize.coerceIn(10f, 48f).sp,
                    fontFamily = fontFamilyFor(textProfile.fontFamily)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            ColorSwatchGrid(
                selectedColor = textProfile.color,
                onColorSelected = { color ->
                    Log.d(TAG, "Selected color: $color")
                    onProfileChanged(textProfile.copy(color = color))
                }
            )
        }
    }
}
