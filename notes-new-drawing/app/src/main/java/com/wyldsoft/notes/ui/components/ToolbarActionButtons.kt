package com.wyldsoft.notes.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel

/**
 * Right-side action buttons in the toolbar: undo, redo, settings, navigation, collapse.
 * Extracted from Toolbar to keep it under 300 lines.
 */
@Composable
fun ToolbarActionButtons(
    viewModel: EditorViewModel,
    onSettingsClick: () -> Unit,
    onNavigateBack: (() -> Unit)?,
    onNavigateForward: (() -> Unit)?,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onCollapse: () -> Unit
) {
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val isPdfNote by viewModel.isPdfNote.collectAsState()

    IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Undo,
            contentDescription = "Undo",
            tint = if (canUndo) Color.Black else Color.LightGray
        )
    }

    IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Redo,
            contentDescription = "Redo",
            tint = if (canRedo) Color.Black else Color.LightGray
        )
    }

    if (isPdfNote) {
        IconButton(onClick = { viewModel.addPdfPage() }) {
            Text("+Pg", fontSize = 11.sp, color = Color.Black)
        }
    }

    IconButton(onClick = onSettingsClick) {
        Icon(imageVector = Icons.Default.Settings, contentDescription = "Note Settings", tint = Color.Black)
    }

    if (onNavigateBack != null && onNavigateForward != null) {
        IconButton(onClick = onNavigateBack, enabled = canGoBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Previous Note",
                tint = if (canGoBack) Color.Black else Color.LightGray
            )
        }
        IconButton(onClick = onNavigateForward, enabled = canGoForward) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next Note",
                tint = if (canGoForward) Color.Black else Color.LightGray
            )
        }

        val noteIndex by viewModel.currentNoteIndex.collectAsState()
        val totalNotes by viewModel.totalNoteCount.collectAsState()
        if (totalNotes > 0) {
            Text(
                text = "$noteIndex/$totalNotes",
                fontSize = 12.sp,
                color = Color.DarkGray
            )
        }
    }

    IconButton(onClick = onCollapse) {
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Collapse Toolbar", tint = Color.Black)
    }
}
