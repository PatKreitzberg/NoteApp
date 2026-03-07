package com.wyldsoft.notes.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.geometry.GeometricShapeType

@Composable
fun ShapeButton(
    selectedShape: GeometricShapeType,
    isGeometryActive: Boolean,
    onActivate: () -> Unit,
    onShapeSelected: (GeometricShapeType) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = {
                if (isGeometryActive) {
                    showDropdown = true
                } else {
                    onActivate()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGeometryActive) Color.Black else Color.Transparent,
                contentColor = if (isGeometryActive) Color.White else Color.Black
            ),
            border = BorderStroke(
                width = if (isGeometryActive) 2.dp else 1.dp,
                color = if (isGeometryActive) Color.Black else Color.Gray
            ),
            modifier = Modifier.size(48.dp),
            contentPadding = PaddingValues(4.dp)
        ) {
            Icon(
                imageVector = iconForShape(selectedShape),
                contentDescription = "Geometry: ${selectedShape.displayName()}",
                modifier = Modifier.size(24.dp)
            )
        }

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false }
        ) {
            GeometricShapeType.entries.forEach { shape ->
                DropdownMenuItem(
                    text = { Text(shape.displayName()) },
                    leadingIcon = {
                        Icon(
                            imageVector = iconForShape(shape),
                            contentDescription = shape.displayName()
                        )
                    },
                    onClick = {
                        onShapeSelected(shape)
                        showDropdown = false
                    }
                )
            }
        }
    }
}

fun iconForShape(shape: GeometricShapeType): ImageVector = when (shape) {
    GeometricShapeType.LINE -> Icons.Default.Remove
    GeometricShapeType.TRIANGLE -> Icons.Default.ChangeHistory
    GeometricShapeType.SQUARE -> Icons.Default.CheckBoxOutlineBlank
    GeometricShapeType.CIRCLE -> Icons.Default.RadioButtonUnchecked
}
