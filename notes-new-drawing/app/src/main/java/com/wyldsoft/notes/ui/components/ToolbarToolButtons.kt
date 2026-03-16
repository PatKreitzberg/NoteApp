package com.wyldsoft.notes.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.presentation.viewmodel.DrawTool
import com.wyldsoft.notes.presentation.viewmodel.EditorMode
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel

/**
 * Draw tab tool buttons: geometry shape button + shape recognition toggle.
 */
@Composable
fun ToolbarToolButtons(
    viewModel: EditorViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    ShapeButton(
        selectedShape = uiState.selectedGeometricShape,
        isGeometryActive = uiState.isGeometryMode,
        onActivate = {
            viewModel.closeStrokeOptions()
            viewModel.switchMode(EditorMode.Draw(DrawTool.GEOMETRY))
        },
        onShapeSelected = { shape -> viewModel.selectGeometricShape(shape) },
        onDropdownOpened = { viewModel.onDropdownOpened() },
        onDropdownClosed = { viewModel.onDropdownClosed() },
        closeSignal = viewModel.closeAllDropdownsEvent
    )

    val isShapeRecOn = uiState.shapeRecognitionEnabled
    IconButton(
        onClick = { viewModel.toggleShapeRecognition() },
        modifier = Modifier
            .size(48.dp)
            .then(
                if (isShapeRecOn) Modifier.border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                else Modifier
            )
    ) {
        Icon(
            imageVector = Icons.Default.AutoFixHigh,
            contentDescription = "Shape Recognition",
            tint = if (isShapeRecOn) Color.Black else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
    }
}
