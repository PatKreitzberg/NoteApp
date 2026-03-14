package com.wyldsoft.notes.editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.wyldsoft.notes.DrawingCanvas
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.ui.components.LiveTextInput
import com.wyldsoft.notes.ui.components.Toolbar
import com.wyldsoft.notes.ui.components.ViewportInfo
import com.wyldsoft.notes.ui.components.dialogs.ManageNoteDialog
import com.wyldsoft.notes.ui.components.dialogs.NoteSettingsDialog
import kotlinx.coroutines.flow.collectLatest

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
    val currentNote by viewModel.currentNote.collectAsState()
    val allNotebooks by viewModel.allNotebooks.collectAsState()
    val noteNotebooks by viewModel.noteNotebooks.collectAsState()
    var showNoteSettingsDialog by remember { mutableStateOf(false) }
    var showManageNotebooksDialog by remember { mutableStateOf(false) }
    var isToolbarCollapsed by remember { mutableStateOf(false) }

    // Close dialogs when triggered by SurfaceView touch listener via closeAllDropdownsEvent
    LaunchedEffect(Unit) {
        viewModel.closeAllDropdownsEvent.collectLatest {
            showNoteSettingsDialog = false
            showManageNotebooksDialog = false
        }
    }

    // Keep EditorViewModel informed when any dialog is open
    LaunchedEffect(showNoteSettingsDialog, showManageNotebooksDialog) {
        viewModel.setDialogOpen(showNoteSettingsDialog || showManageNotebooksDialog)
    }

    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val hasNotebook = viewModel.notebookId != null
    val textInputPosition by viewModel.textInputPosition.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(horizontal = if (isToolbarCollapsed) 0.dp else 8.dp)) {
                Toolbar(
                    viewModel = viewModel,
                    currentPenProfile = currentPenProfile,
                    onSettingsClick = {
                        viewModel.loadNoteManagementData()
                        showNoteSettingsDialog = true
                    },
                    onCollapsedChanged = { collapsed -> isToolbarCollapsed = collapsed },
                    onNavigateBack = if (hasNotebook) {{ viewModel.navigateBackward() }} else null,
                    onNavigateForward = if (hasNotebook) {{ viewModel.navigateForward() }} else null,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                DrawingCanvas(
                    viewModel = viewModel,
                    onSurfaceViewCreated = onSurfaceViewCreated
                )
                textInputPosition?.let { position ->
                    LiveTextInput(
                        notePosition = position,
                        viewModel = viewModel,
                        onCommit = { viewModel.commitLiveTextInput() }
                    )
                }
            }
        }

        ViewportInfo(
            viewportState = viewportState,
            isPaginationEnabled = isPaginationEnabled,
            currentPageNumber = currentPageNumber,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showNoteSettingsDialog) {
        NoteSettingsDialog(
            noteName = currentNote.title,
            isPaginationEnabled = isPaginationEnabled,
            currentPaperSize = paperSize,
            currentPaperTemplate = paperTemplate,
            onRenameNote = { newName -> viewModel.renameNote(newName) },
            onPaginationToggle = { enabled -> viewModel.updatePaginationEnabled(enabled) },
            onPaperSizeChange = { newPaperSize -> viewModel.updatePaperSize(newPaperSize) },
            onPaperTemplateChange = { newTemplate -> viewModel.updatePaperTemplate(newTemplate) },
            onManageNotebooks = {
                showNoteSettingsDialog = false
                showManageNotebooksDialog = true
            },
            onDismiss = {
                showNoteSettingsDialog = false
                viewModel.forceRefresh()
            }
        )
    }

    if (showManageNotebooksDialog) {
        ManageNoteDialog(
            notebooks = allNotebooks,
            checkedNotebookIds = noteNotebooks,
            onSave = { notebookIds -> viewModel.updateNoteNotebooks(notebookIds) },
            onDismiss = { showManageNotebooksDialog = false }
        )
    }
}
