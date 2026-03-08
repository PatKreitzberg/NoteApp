package com.wyldsoft.notes.ui.components

import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.Tool

/**
 * Geometry and selection tool buttons in the toolbar.
 * Extracted from Toolbar to keep it under 300 lines.
 */
@Composable
fun ToolbarToolButtons(
    viewModel: EditorViewModel,
    isStrokeSelectionOpen: Boolean,
    onCloseStrokePanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isGeometryActive = uiState.selectedTool == Tool.GEOMETRY
    val isSelectionActive = uiState.selectedTool == Tool.SELECTOR

    ShapeButton(
        selectedShape = uiState.selectedGeometricShape,
        isGeometryActive = isGeometryActive,
        onActivate = {
            if (isStrokeSelectionOpen) onCloseStrokePanel()
            viewModel.selectTool(Tool.GEOMETRY)
        },
        onShapeSelected = { shape -> viewModel.selectGeometricShape(shape) }
    )

    IconButton(
        onClick = {
            if (isSelectionActive) {
                viewModel.cancelSelection()
            } else {
                if (isStrokeSelectionOpen) onCloseStrokePanel()
                viewModel.selectTool(Tool.SELECTOR)
            }
        },
        modifier = Modifier.then(if (isSelectionActive) Modifier.border(2.dp, Color.Black) else Modifier)
    ) {
        Icon(
            imageVector = Icons.Default.SelectAll,
            contentDescription = "Selection Tool",
            tint = if (isSelectionActive) Color.Black else Color.Gray
        )
    }
}
