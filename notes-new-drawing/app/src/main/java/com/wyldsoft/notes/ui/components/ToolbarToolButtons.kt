package com.wyldsoft.notes.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.wyldsoft.notes.presentation.viewmodel.DrawTool
import com.wyldsoft.notes.presentation.viewmodel.EditorMode
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel

/**
 * Draw tab tool buttons: geometry shape button.
 * Selection moved to ToolbarEditButtons (Edit tab).
 */
@Composable
fun ToolbarToolButtons(
    viewModel: EditorViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val mode = uiState.mode
    val isGeometryActive = mode is EditorMode.Draw && mode.drawTool == DrawTool.GEOMETRY

    ShapeButton(
        selectedShape = uiState.selectedGeometricShape,
        isGeometryActive = isGeometryActive,
        onActivate = {
            viewModel.closeStrokeOptions()
            viewModel.switchMode(EditorMode.Draw(DrawTool.GEOMETRY))
        },
        onShapeSelected = { shape -> viewModel.selectGeometricShape(shape) },
        onDropdownOpened = { viewModel.onDropdownOpened() },
        onDropdownClosed = { viewModel.onDropdownClosed() },
        closeSignal = viewModel.closeAllDropdownsEvent
    )
}
