package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
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
    val inGeometry = currentMode == AppMode.GEOMETRY

    Box {
        IconButton(
            onClick = {
                Log.d(TAG, "GeometryButton clicked inGeometry=$inGeometry")
                if (!inGeometry) {
                    EditorState.setMode(AppMode.GEOMETRY)
                    onGeometryExpandedChange(false)
                } else {
                    onGeometryExpandedChange(!geometryExpanded)
                }
            },
            modifier = if (inGeometry) Modifier.border(2.dp, Color.Black) else Modifier
        ) {
            Icon(
                imageVector = Icons.Default.Category,
                contentDescription = "Geometry Tool",
                tint = if (inGeometry) Color.Black else Color.Gray
            )
        }

        DropdownMenu(
            expanded = inGeometry && geometryExpanded,
            onDismissRequest = { onGeometryExpandedChange(false) }
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
                    }
                )
            }
        }
    }
}
