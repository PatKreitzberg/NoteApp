package com.wyldsoft.notes.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

import com.wyldsoft.notes.DrawingCanvas
import com.wyldsoft.notes.export.ExportAction
import com.wyldsoft.notes.export.ExportScope
import com.wyldsoft.notes.export.PdfExporter
import com.wyldsoft.notes.export.dispatchExport
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.settings.DisplaySettingsRepository
import com.wyldsoft.notes.ui.components.LiveTextInput
import com.wyldsoft.notes.ui.components.Toolbar
import com.wyldsoft.notes.ui.components.VerticalScrollBar
import com.wyldsoft.notes.ui.components.ViewportInfo
import com.wyldsoft.notes.ui.components.dialogs.ExportDialog
import com.wyldsoft.notes.ui.components.dialogs.ManageNoteDialog
import com.wyldsoft.notes.ui.components.dialogs.NoteSettingsDialog
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditorView(
    viewModel: EditorViewModel,
    displaySettingsRepository: DisplaySettingsRepository? = null,
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
    val contentMaxY by viewModel.contentMaxY.collectAsState()
    val showScrollBar by (displaySettingsRepository?.scrollBarVisible
        ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    var showNoteSettingsDialog by remember { mutableStateOf(false) }
    var showManageNotebooksDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isToolbarCollapsed by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingExportFile by remember { mutableStateOf<File?>(null) }
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { dest ->
            val src = pendingExportFile ?: return@let
            coroutineScope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(dest)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
            }
        }
    }

    fun launchExport(scope: ExportScope, action: ExportAction) {
        coroutineScope.launch(Dispatchers.IO) {
            val noteWidthPx = context.resources.displayMetrics.widthPixels
            val exporter = PdfExporter(context)
            val note = viewModel.currentNote.value
            val notebookId = viewModel.notebookId
            val file = when {
                scope == ExportScope.NOTEBOOK && notebookId != null -> {
                    val notes = viewModel.getNotebookNotesForExport(notebookId)
                    val nbName = viewModel.getNotebookName(notebookId)
                    exporter.exportNotebook(notes, nbName, noteWidthPx)
                }
                else -> exporter.exportNote(note, noteWidthPx)
            }
            withContext(Dispatchers.Main) {
                dispatchExport(context, file, action, saveFileLauncher) { pending ->
                    pendingExportFile = pending
                }
            }
        }
    }

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
                if (showScrollBar) {
                    VerticalScrollBar(
                        scrollY = viewportState.scrollY,
                        contentMaxY = contentMaxY,
                        scale = viewportState.scale,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(16.dp)
                            .fillMaxHeight()
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
            onExport = {
                showNoteSettingsDialog = false
                showExportDialog = true
            },
            onDismiss = {
                showNoteSettingsDialog = false
                viewModel.forceRefresh()
            }
        )
    }

    if (showExportDialog) {
        val notebookId = viewModel.notebookId
        var notebookName by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(notebookId) {
            notebookName = notebookId?.let { viewModel.getNotebookName(it) }
        }
        ExportDialog(
            noteName = currentNote.title,
            notebookName = notebookName,
            defaultScope = ExportScope.SINGLE_NOTE,
            onExport = { scope, action ->
                showExportDialog = false
                launchExport(scope, action)
            },
            onDismiss = { showExportDialog = false }
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
