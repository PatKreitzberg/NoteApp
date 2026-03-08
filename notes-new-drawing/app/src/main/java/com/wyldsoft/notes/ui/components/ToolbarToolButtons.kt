package com.wyldsoft.notes.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.Tool

/**
 * Draw tab tool buttons: geometry shape button.
 * Selection moved to ToolbarEditButtons (Edit tab).
 */
@Composable
fun ToolbarToolButtons(
    viewModel: EditorViewModel,
    isStrokeSelectionOpen: Boolean,
    onCloseStrokePanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isGeometryActive = uiState.selectedTool == Tool.GEOMETRY

    ShapeButton(
        selectedShape = uiState.selectedGeometricShape,
        isGeometryActive = isGeometryActive,
        onActivate = {
            if (isStrokeSelectionOpen) onCloseStrokePanel()
            viewModel.selectTool(Tool.GEOMETRY)
        },
        onShapeSelected = { shape -> viewModel.selectGeometricShape(shape) }
    )
}
