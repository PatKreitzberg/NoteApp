package com.wyldsoft.notes.editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.wyldsoft.notes.DrawingCanvas
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.data.repository.NotebookRepository
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.ViewModelFactory
import com.wyldsoft.notes.ui.components.Toolbar
import com.wyldsoft.notes.ui.components.ViewportInfo
import com.wyldsoft.notes.ui.components.dialogs.NoteSettingsDialog
import com.wyldsoft.notes.domain.models.PaperSize

@Composable
fun EditorView(
    noteRepository: NoteRepository,
    notebookRepository: NotebookRepository,
    onSurfaceViewCreated: (android.view.SurfaceView) -> Unit = {}
) {
    val viewModel = EditorViewModel(noteRepository, notebookRepository)

    val uiState by viewModel.uiState.collectAsState()
    val currentPenProfile by viewModel.currentPenProfile.collectAsState()
    val viewportState by viewModel.viewportState.collectAsState()
    val isPaginationEnabled by viewModel.isPaginationEnabled.collectAsState()
    val paperSize by viewModel.paperSize.collectAsState()
    val currentPageNumber by viewModel.currentPageNumber.collectAsState()
    var showNoteSettingsDialog by remember { mutableStateOf(false) }
    
    // Pass ViewModel to the activity
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(viewModel) {
        (context as? com.wyldsoft.notes.drawing.DrawingActivityInterface)?.setViewModel(viewModel)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Toolbar(
                viewModel = viewModel,
                currentPenProfile = currentPenProfile,
                isStrokeOptionsOpen = uiState.isStrokeOptionsOpen,
                onSettingsClick = { showNoteSettingsDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

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
            onPaginationToggle = { enabled ->
                viewModel.updatePaginationEnabled(enabled)
            },
            onPaperSizeChange = { newPaperSize ->
                viewModel.updatePaperSize(newPaperSize)
            },
            onDismiss = { showNoteSettingsDialog = false }
        )
    }
}