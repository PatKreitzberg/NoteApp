package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.editor.AppMode
import com.wyldsoft.notes.editor.EditorState
import com.wyldsoft.notes.geometry.GeometryShapeType

private const val TAG = "GeometryButton"

@Composable
fun GeometryButton(
    geometryExpanded: Boolean,
    onGeometryExpandedChange: (Boolean) -> Unit
) {
    val currentMode by EditorState.currentMode.collectAsState()
    val activeShape by EditorState.activeGeometryShape.collectAsState()

    // Active when in GEOMETRY mode, or in SETTINGS mode because the geometry dropdown opened it
    val inGeometryContext = currentMode == AppMode.GEOMETRY ||
            (currentMode == AppMode.SETTINGS && geometryExpanded)

    Box {
        IconButton(
            onClick = {
                Log.d(TAG, "GeometryButton clicked inGeometryContext=$inGeometryContext geometryExpanded=$geometryExpanded")
                when {
                    !inGeometryContext -> {
                        // Enter geometry mode for the first time
                        EditorState.setMode(AppMode.GEOMETRY)
                        onGeometryExpandedChange(false)
                    }
                    !geometryExpanded -> {
                        // Already in geometry — open the shape picker.
                        // Switch to SETTINGS to suppress Onyx ink while the menu is open.
                        EditorState.setMode(AppMode.SETTINGS)
                        onGeometryExpandedChange(true)
                    }
                    else -> {
                        // Dropdown already open — close it and return to geometry
                        onGeometryExpandedChange(false)
                        EditorState.setMode(AppMode.GEOMETRY)
                    }
                }
            },
            modifier = if (inGeometryContext) Modifier.border(2.dp, Color.Black) else Modifier
        ) {
            Icon(
                imageVector = when (activeShape) {
                    GeometryShapeType.CIRCLE -> Icons.Default.RadioButtonUnchecked
                    GeometryShapeType.LINE -> Icons.Default.HorizontalRule
                    GeometryShapeType.RECTANGLE -> Icons.Default.CropSquare
                    GeometryShapeType.TRIANGLE -> Icons.Default.ChangeHistory
                },
                contentDescription = "Geometry Tool: ${activeShape.name.lowercase()}",
                tint = if (inGeometryContext) Color.Black else Color.Gray
            )
        }

        // Dropdown visibility is driven solely by geometryExpanded — not inGeometryContext,
        // since mode is SETTINGS while the dropdown is open.
        DropdownMenu(
            expanded = geometryExpanded,
            onDismissRequest = {
                Log.d(TAG, "geometry dropdown dismissed")
                onGeometryExpandedChange(false)
                EditorState.setMode(AppMode.GEOMETRY)
            }
        ) {
            GeometryShapeType.entries.forEach { shapeType ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = shapeType.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (activeShape == shapeType) Color.Black else Color.Gray
                        )
                    },
                    onClick = {
                        Log.d(TAG, "Geometry shape selected: $shapeType")
                        EditorState.setActiveGeometryShape(shapeType)
                        onGeometryExpandedChange(false)
                        EditorState.setMode(AppMode.GEOMETRY)
                    }
                )
            }
        }
    }
}
