package com.wyldsoft.notes.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
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
        SelectableIconButton(
            icon = iconForShape(selectedShape),
            contentDescription = "Geometry: ${selectedShape.displayName()}",
            isSelected = isGeometryActive,
            onClick = {
                if (isGeometryActive) {
                    showDropdown = true
                } else {
                    onActivate()
                }
            },
            size = 48.dp,
            iconSize = 24.dp
        )

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
