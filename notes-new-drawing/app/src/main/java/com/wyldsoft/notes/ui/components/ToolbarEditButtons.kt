package com.wyldsoft.notes.ui.components

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
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
 * Edit tab buttons: selection, copy, paste.
 * Shown when the Edit tab is active in the toolbar.
 */
@Composable
fun ToolbarEditButtons(
    viewModel: EditorViewModel,
    isStrokeSelectionOpen: Boolean,
    onCloseStrokePanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val copiedShapes by viewModel.copiedShapes.collectAsState()
    val hasSelection by viewModel.hasSelection.collectAsState()
    val isSelectionActive = uiState.selectedTool == Tool.SELECTOR
    val hasCopied = copiedShapes.isNotEmpty()

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

    IconButton(
        onClick = {
            Log.d("ToolbarEditButtons", "Copy button clicked. Has selection: $hasSelection")
            viewModel.copySelection()
                  },
        enabled = hasSelection
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy Selection",
            tint = if (hasSelection) Color.Black else Color.LightGray
        )
    }

    IconButton(
        onClick = { viewModel.pasteSelection() },
        enabled = hasCopied
    ) {
        Icon(
            imageVector = Icons.Default.ContentPaste,
            contentDescription = "Paste",
            tint = if (hasCopied) Color.Black else Color.LightGray
        )
    }
}
