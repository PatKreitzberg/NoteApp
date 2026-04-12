package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

private const val TAG = "ColorSwatchGrid"

/**
 * Organized color palette for the pen/text color picker.
 * Each inner list is one row, arranged dark → light within the hue family.
 * 8 columns × 8 hue rows = 64 colors.
 */
val colorPalette: List<List<Pair<Color, String>>> = listOf(
    // Neutrals — black to white
    listOf(
        Color(0xFF000000) to "Black",
        Color(0xFF222222) to "Near Black",
        Color(0xFF444444) to "Very Dark Gray",
        Color(0xFF666666) to "Dark Gray",
        Color(0xFF888888) to "Gray",
        Color(0xFFAAAAAA) to "Silver",
        Color(0xFFCCCCCC) to "Light Gray",
        Color(0xFFFFFFFF) to "White",
    ),
    // Reds — dark maroon to blush
    listOf(
        Color(0xFF4A0000) to "Dark Maroon",
        Color(0xFF800000) to "Maroon",
        Color(0xFFCC0000) to "Dark Red",
        Color(0xFFFF0000) to "Red",
        Color(0xFFFF4444) to "Bright Red",
        Color(0xFFFF8888) to "Light Red",
        Color(0xFFFFBBBB) to "Pale Red",
        Color(0xFFFFE0E0) to "Blush",
    ),
    // Oranges / Browns — dark brown to cream
    listOf(
        Color(0xFF3D1C00) to "Dark Brown",
        Color(0xFF7A3800) to "Brown",
        Color(0xFFC06000) to "Sienna",
        Color(0xFFE87800) to "Dark Orange",
        Color(0xFFFF9500) to "Orange",
        Color(0xFFFFB347) to "Light Orange",
        Color(0xFFFFD093) to "Peach",
        Color(0xFFFFF0D0) to "Cream",
    ),
    // Yellows — dark olive to pale yellow
    listOf(
        Color(0xFF3D3500) to "Dark Olive",
        Color(0xFF7A6A00) to "Olive",
        Color(0xFFC0A800) to "Dark Yellow",
        Color(0xFFE8D000) to "Gold",
        Color(0xFFFFEE00) to "Yellow",
        Color(0xFFFFF44F) to "Bright Yellow",
        Color(0xFFFFFAAA) to "Pale Yellow",
        Color(0xFFFFFDE0) to "Very Pale Yellow",
    ),
    // Greens — dark forest to mint
    listOf(
        Color(0xFF003300) to "Very Dark Green",
        Color(0xFF005500) to "Dark Green",
        Color(0xFF007A00) to "Forest Green",
        Color(0xFF00AA00) to "Green",
        Color(0xFF33CC33) to "Bright Green",
        Color(0xFF77DD77) to "Light Green",
        Color(0xFFAAEAAA) to "Pale Green",
        Color(0xFFD5F5D5) to "Mint",
    ),
    // Teals / Cyans — dark teal to pale cyan
    listOf(
        Color(0xFF003333) to "Very Dark Teal",
        Color(0xFF005555) to "Dark Teal",
        Color(0xFF007A7A) to "Teal",
        Color(0xFF009999) to "Dark Cyan",
        Color(0xFF00CCCC) to "Cyan",
        Color(0xFF33DDDD) to "Bright Cyan",
        Color(0xFF88EEEE) to "Light Cyan",
        Color(0xFFCCFAFA) to "Pale Cyan",
    ),
    // Blues — dark navy to pale blue
    listOf(
        Color(0xFF000033) to "Dark Navy",
        Color(0xFF000066) to "Navy",
        Color(0xFF0000AA) to "Dark Blue",
        Color(0xFF0033CC) to "Blue",
        Color(0xFF2255EE) to "Royal Blue",
        Color(0xFF4488FF) to "Light Blue",
        Color(0xFF88AAFF) to "Pale Blue",
        Color(0xFFCCDCFF) to "Very Pale Blue",
    ),
    // Purples / Pinks — dark purple to very pale pink
    listOf(
        Color(0xFF330033) to "Very Dark Purple",
        Color(0xFF660066) to "Dark Purple",
        Color(0xFF990099) to "Purple",
        Color(0xFFCC00CC) to "Magenta",
        Color(0xFFDD44BB) to "Pink",
        Color(0xFFEE77CC) to "Light Pink",
        Color(0xFFF5AADD) to "Pale Pink",
        Color(0xFFFBD5F5) to "Very Pale Pink",
    ),
)

// Backward-compatible flat list for any existing references
val colorSwatches: List<Pair<Color, String>> = colorPalette.flatten()

@Composable
private fun ColorSwatch(
    color: Color,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLight = color.luminance() > 0.6f
    val checkColor = if (isLight) Color(0xFF222222) else Color.White
    val borderColor = when {
        isSelected -> if (isLight) Color(0xFF333333) else Color.White
        isLight -> Color.Gray.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val borderWidth = if (isSelected) 2.dp else 0.5.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .clickable {
                Log.d(TAG, "Selected color: $name")
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected: $name",
                tint = checkColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Full-width color swatch grid organized by hue (dark → light per row).
 * Shared by PenPropertiesPanel and TextPropertiesPanel.
 */
@Composable
fun ColorSwatchGrid(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "ColorSwatchGrid selectedColor=$selectedColor")
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        colorPalette.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                row.forEach { (color, name) ->
                    ColorSwatch(
                        color = color,
                        name = name,
                        isSelected = color == selectedColor,
                        onClick = { onColorSelected(color) },
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
