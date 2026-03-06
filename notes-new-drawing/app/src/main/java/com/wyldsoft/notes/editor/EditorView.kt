package com.wyldsoft.notes.editor

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.wyldsoft.notes.DrawingCanvas
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.ui.components.Toolbar
import com.wyldsoft.notes.ui.components.ViewportInfo
import com.wyldsoft.notes.ui.components.dialogs.NoteSettingsDialog

@Composable
fun EditorView(
    viewModel: EditorViewModel,
    onSurfaceViewCreated: (android.view.SurfaceView, EditorViewModel) -> Unit = {_, _ -> },
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentPenProfile by viewModel.currentPenProfile.collectAsState()
    val viewportState by viewModel.viewportState.collectAsState()
    val isPaginationEnabled by viewModel.isPaginationEnabled.collectAsState()
    val paperSize by viewModel.paperSize.collectAsState()
    val paperTemplate by viewModel.paperTemplate.collectAsState()
    val currentPageNumber by viewModel.currentPageNumber.collectAsState()
    var showNoteSettingsDialog by remember { mutableStateOf(false) }
    var isToolbarCollapsed by remember { mutableStateOf(false) }

    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val hasNotebook = viewModel.notebookId != null

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Toolbar has its own horizontal padding; canvas gets full width
            Box(modifier = Modifier.padding(horizontal = if (isToolbarCollapsed) 0.dp else 8.dp)) {
                Toolbar(
                    viewModel = viewModel,
                    currentPenProfile = currentPenProfile,
                    isStrokeOptionsOpen = uiState.isStrokeOptionsOpen,
                    onSettingsClick = { showNoteSettingsDialog = true },
                    onCollapsedChanged = { collapsed -> isToolbarCollapsed = collapsed },
                    onNavigateBack = if (hasNotebook) {{ viewModel.navigateBackward() }} else null,
                    onNavigateForward = if (hasNotebook) {{ viewModel.navigateForward() }} else null,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward
                )
            }

            DrawingCanvas(
                viewModel = viewModel,
                onSurfaceViewCreated = onSurfaceViewCreated
            )
        }
        
        // Viewport info overlay at the bottom
        ViewportInfo(
            viewportState = viewportState,
            isPaginationEnabled = isPaginationEnabled,
            currentPageNumber = currentPageNumber,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    
    // Note settings dialog
    if (showNoteSettingsDialog) {
        NoteSettingsDialog(
            isPaginationEnabled = isPaginationEnabled,
            currentPaperSize = paperSize,
            currentPaperTemplate = paperTemplate,
            onPaginationToggle = { enabled ->
                viewModel.updatePaginationEnabled(enabled)
            },
            onPaperSizeChange = { newPaperSize ->
                viewModel.updatePaperSize(newPaperSize)
            },
            onPaperTemplateChange = { newTemplate ->
                viewModel.updatePaperTemplate(newTemplate)
            },
            onDismiss = { showNoteSettingsDialog = false }
        )
    }
}