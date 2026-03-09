package com.wyldsoft.notes.ui.components

import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
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

@Composable
fun ToolbarTextButtons(
    viewModel: EditorViewModel,
    isStrokeSelectionOpen: Boolean,
    onCloseStrokePanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isTextActive = uiState.selectedTool == Tool.TEXT

    IconButton(
        onClick = {
            if (isTextActive) {
                viewModel.selectTool(Tool.PEN)
            } else {
                if (isStrokeSelectionOpen) onCloseStrokePanel()
                viewModel.selectTool(Tool.TEXT)
            }
        },
        modifier = Modifier.then(if (isTextActive) Modifier.border(2.dp, Color.Black) else Modifier)
    ) {
        Icon(
            imageVector = Icons.Default.TextFields,
            contentDescription = "Text Tool",
            tint = if (isTextActive) Color.Black else Color.Gray
        )
    }
}
