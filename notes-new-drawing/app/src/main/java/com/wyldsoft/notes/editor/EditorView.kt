package com.wyldsoft.notes.editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.wyldsoft.notes.DrawingCanvas
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.ViewModelFactory
import com.wyldsoft.notes.ui.components.Toolbar
import com.wyldsoft.notes.ui.components.ViewportInfo
import com.wyldsoft.notes.ui.components.dialogs.NoteSettingsDialog

@Composable
fun EditorView(
    viewModelFactory: ViewModelFactory,
    onSurfaceViewCreated: (android.view.SurfaceView) -> Unit = {}
) {
    val viewModel: EditorViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val currentPenProfile by viewModel.currentPenProfile.collectAsState()
    val viewportState by viewModel.viewportState.collectAsState()
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
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    
    // Note settings dialog
    if (showNoteSettingsDialog) {
        NoteSettingsDialog(
            onDismiss = { showNoteSettingsDialog = false }
        )
    }
}