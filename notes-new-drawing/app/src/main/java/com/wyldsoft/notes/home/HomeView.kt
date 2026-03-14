package com.wyldsoft.notes.home

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.State
import kotlinx.coroutines.launch
import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NoteEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.repository.FolderRepository
import com.wyldsoft.notes.presentation.viewmodel.HomeViewModel
import com.wyldsoft.notes.gestures.GestureSettingsRepository
import com.wyldsoft.notes.presentation.viewmodel.SyncViewModel
import com.wyldsoft.notes.settings.DisplaySettingsRepository
import com.wyldsoft.notes.ui.components.dialogs.AppSettingsDialog
import com.wyldsoft.notes.ui.components.dialogs.GoogleDriveDialog
import com.wyldsoft.notes.ui.components.dialogs.ManageNoteDialog

@Composable
fun HomeView(
    viewModel: HomeViewModel,
    gestureSettingsRepository: GestureSettingsRepository,
    displaySettingsRepository: DisplaySettingsRepository,
    signInLauncher: ActivityResultLauncher<Intent>,
    signInError: State<String?>,
    syncViewModel: SyncViewModel,
    onNotebookSelected: (String, String) -> Unit
) {
    val currentFolder by viewModel.currentFolder.collectAsState()
    val folderPath by viewModel.folderPath.collectAsState()
    val subfolders by viewModel.subfolders.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()
    val looseNotes by viewModel.looseNotes.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    val allNotebooks by viewModel.allNotebooks.collectAsState()
    val showCreateFolderDialog by viewModel.showCreateFolderDialog.collectAsState()
    val showCreateNotebookDialog by viewModel.showCreateNotebookDialog.collectAsState()
    var showAppSettingsDialog by remember { mutableStateOf(false) }
    var showGoogleDriveDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    // Context menu targets
    var contextMenuNotebook by remember { mutableStateOf<NotebookEntity?>(null) }
    var contextMenuFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var contextMenuNote by remember { mutableStateOf<NoteEntity?>(null) }

    // Active dialog state
    var showMoveNotebookDialog by remember { mutableStateOf(false) }
    var showMoveFolderDialog by remember { mutableStateOf(false) }
    var showMoveNoteDialog by remember { mutableStateOf(false) }
    var showDeleteNotebookDialog by remember { mutableStateOf(false) }
    var showDeleteFolderDialog by remember { mutableStateOf(false) }
    var showDeleteNoteDialog by remember { mutableStateOf(false) }
    var showPermanentDeleteNotebookDialog by remember { mutableStateOf(false) }
    var showPermanentDeleteFolderDialog by remember { mutableStateOf(false) }
    var showPermanentDeleteNoteDialog by remember { mutableStateOf(false) }
    var showRenameNotebookDialog by remember { mutableStateOf(false) }
    var showRenameFolderDialog by remember { mutableStateOf(false) }
    var showRenameNoteDialog by remember { mutableStateOf(false) }
    var showManageNoteDialog by remember { mutableStateOf(false) }
    var manageNoteCurrentNotebooks by remember { mutableStateOf<List<String>>(emptyList()) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }

    val isInTrash = currentFolder?.id == FolderRepository.TRASH_FOLDER_ID

    // Load folders/notebooks when a context menu action needs them
    fun ensureFoldersLoaded() { viewModel.loadAllFoldersAndNotebooks() }

    if (showCreateNotebookDialog) {
        Log.d("HomeView", "Show create notebook dialog if statement")
        CreateItemDialog(
            title = "Create Notebook",
            placeholder = "Notebook name",
            onConfirm = { viewModel.createNotebook(it) },
            onDismiss = {
                Log.d("HomeView", "Hiding create notebook dialog")
                viewModel.hideCreateNotebookDialog()
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                BreadcrumbBar(folderPath = folderPath, onFolderClick = { viewModel.navigateToFolder(it.id) })
            }
            IconButton(onClick = { showSearchDialog = true }) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search Notes")
            }
            IconButton(onClick = { showAppSettingsDialog = true }) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "App Settings")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(title = "Folders", onAddClick = { viewModel.showCreateFolderDialog() })
        FolderRow(
            folders = subfolders,
            onFolderClick = { viewModel.navigateToFolder(it.id) },
            onFolderLongClick = { folder ->
                contextMenuFolder = folder
                ensureFoldersLoaded()
            }
        )

        // Folder context menu
        contextMenuFolder?.let { folder ->
            if (viewModel.isTrashFolder(folder.id)) {
                TrashFolderContextMenu(
                    onEmptyTrash = { showEmptyTrashDialog = true; contextMenuFolder = null },
                    onDismiss = { contextMenuFolder = null }
                )
            } else if (isInTrash) {
                TrashItemFolderContextMenu(
                    onRestore = { viewModel.restoreFolder(folder.id); contextMenuFolder = null },
                    onDeletePermanently = { showPermanentDeleteFolderDialog = true },
                    onDismiss = { contextMenuFolder = null }
                )
            } else {
                FolderContextMenu(
                    onMove = { showMoveFolderDialog = true },
                    onRename = { showRenameFolderDialog = true },
                    onDelete = { showDeleteFolderDialog = true },
                    onDismiss = { contextMenuFolder = null }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(
            title = "Notebooks",
            onAddClick = {
                Log.d("HomeView", "Show create notebook dialog onAddClick")
                viewModel.showCreateNotebookDialog()
            }
        )
        NotebookRow(
            notebooks = notebooks,
            viewModel = viewModel,
            onNotebookSelected = onNotebookSelected,
            onNotebookLongClick = { notebook ->
                contextMenuNotebook = notebook
                ensureFoldersLoaded()
            }
        )

        // Notebook context menu
        contextMenuNotebook?.let { notebook ->
            if (isInTrash) {
                TrashItemNotebookContextMenu(
                    onRestore = { viewModel.restoreNotebook(notebook.id); contextMenuNotebook = null },
                    onDeletePermanently = { showPermanentDeleteNotebookDialog = true },
                    onDismiss = { contextMenuNotebook = null }
                )
            } else {
                NotebookContextMenu(
                    onMove = { showMoveNotebookDialog = true },
                    onRename = { showRenameNotebookDialog = true },
                    onDelete = { showDeleteNotebookDialog = true },
                    onDismiss = { contextMenuNotebook = null }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "Notes", onAddClick = { viewModel.createLooseNote() })
        NoteRow(
            notes = looseNotes,
            onNoteClick = { note ->
                // Open loose note: navigate to it without a notebook context
                onNotebookSelected("", note.id)
            },
            onNoteLongClick = { note ->
                contextMenuNote = note
                ensureFoldersLoaded()
            }
        )

        // Note context menu
        contextMenuNote?.let { note ->
            if (isInTrash) {
                TrashItemNoteContextMenu(
                    onRestore = { viewModel.restoreNote(note.id); contextMenuNote = null },
                    onDeletePermanently = { showPermanentDeleteNoteDialog = true },
                    onDismiss = { contextMenuNote = null }
                )
            } else {
                NoteContextMenu(
                    onMove = { showMoveNoteDialog = true },
                    onRename = { showRenameNoteDialog = true },
                    onDelete = { showDeleteNoteDialog = true },
                    onManage = {
                        coroutineScope.launch {
                            manageNoteCurrentNotebooks = viewModel.getNotebooksForNote(note.id)
                            showManageNoteDialog = true
                        }
                    },
                    onDismiss = { contextMenuNote = null }
                )
            }
        }
    }

    if (showCreateFolderDialog) {
        CreateItemDialog(
            title = "Create Folder",
            placeholder = "Folder name",
            onConfirm = { viewModel.createFolder(it) },
            onDismiss = { viewModel.hideCreateFolderDialog() }
        )
    }

    if (showAppSettingsDialog) {
        AppSettingsDialog(
            gestureSettingsRepository = gestureSettingsRepository,
            displaySettingsRepository = displaySettingsRepository,
            onDismiss = { showAppSettingsDialog = false },
            onOpenGoogleDrive = { showAppSettingsDialog = false; showGoogleDriveDialog = true }
        )
    }

    if (showGoogleDriveDialog) {
        GoogleDriveDialog(
            signInLauncher = signInLauncher,
            signInError = signInError,
            syncViewModel = syncViewModel,
            onDismiss = { showGoogleDriveDialog = false }
        )
    }

    // Notebook dialogs
    if (showRenameNotebookDialog) {
        contextMenuNotebook?.let { notebook ->
            RenameDialog(
                title = "Rename Notebook",
                currentName = notebook.name,
                onConfirm = { viewModel.renameNotebook(notebook.id, it) },
                onDismiss = { showRenameNotebookDialog = false; contextMenuNotebook = null }
            )
        }
    }

    if (showDeleteNotebookDialog) {
        contextMenuNotebook?.let { notebook ->
            ConfirmDeleteDialog(
                title = "Move to Trash",
                message = "Move \"${notebook.name}\" to Trash?",
                onConfirm = { viewModel.deleteNotebook(notebook.id) },
                onDismiss = { showDeleteNotebookDialog = false; contextMenuNotebook = null }
            )
        }
    }

    if (showMoveNotebookDialog) {
        contextMenuNotebook?.let { notebook ->
            MoveToFolderDialog(
                folders = allFolders,
                excludeFolderId = null,
                title = "Move Notebook to Folder",
                onMove = { folder -> viewModel.moveNotebook(notebook.id, folder.id) },
                onDismiss = { showMoveNotebookDialog = false; contextMenuNotebook = null }
            )
        }
    }

    // Folder dialogs
    if (showRenameFolderDialog) {
        contextMenuFolder?.let { folder ->
            RenameDialog(
                title = "Rename Folder",
                currentName = folder.name,
                onConfirm = { viewModel.renameFolder(folder.id, it) },
                onDismiss = { showRenameFolderDialog = false; contextMenuFolder = null }
            )
        }
    }

    if (showDeleteFolderDialog) {
        contextMenuFolder?.let { folder ->
            ConfirmDeleteDialog(
                title = "Move to Trash",
                message = "Move \"${folder.name}\" and all its contents to Trash?",
                onConfirm = { viewModel.deleteFolder(folder.id) },
                onDismiss = { showDeleteFolderDialog = false; contextMenuFolder = null }
            )
        }
    }

    if (showMoveFolderDialog) {
        contextMenuFolder?.let { folder ->
            MoveToFolderDialog(
                folders = allFolders,
                excludeFolderId = folder.id,
                title = "Move Folder into Folder",
                onMove = { target -> viewModel.moveFolder(folder.id, target.id) },
                onDismiss = { showMoveFolderDialog = false; contextMenuFolder = null }
            )
        }
    }

    // Note dialogs
    if (showRenameNoteDialog) {
        contextMenuNote?.let { note ->
            RenameDialog(
                title = "Rename Note",
                currentName = note.title,
                onConfirm = { viewModel.renameNote(note.id, it) },
                onDismiss = { showRenameNoteDialog = false; contextMenuNote = null }
            )
        }
    }

    if (showDeleteNoteDialog) {
        contextMenuNote?.let { note ->
            ConfirmDeleteDialog(
                title = "Move to Trash",
                message = "Move \"${note.title}\" to Trash?",
                onConfirm = { viewModel.deleteNote(note.id) },
                onDismiss = { showDeleteNoteDialog = false; contextMenuNote = null }
            )
        }
    }

    if (showMoveNoteDialog) {
        contextMenuNote?.let { note ->
            MoveNoteDialog(
                notebooks = allNotebooks,
                folders = allFolders,
                onMoveToNotebook = { notebook -> viewModel.moveNoteToNotebook(note.id, notebook.id) },
                onMoveToFolder = { folder -> viewModel.moveNoteToFolder(note.id, folder.id) },
                onDismiss = { showMoveNoteDialog = false; contextMenuNote = null }
            )
        }
    }

    if (showManageNoteDialog) {
        contextMenuNote?.let { note ->
            ManageNoteDialog(
                notebooks = allNotebooks,
                checkedNotebookIds = manageNoteCurrentNotebooks,
                onSave = { notebookIds -> viewModel.updateNoteNotebooks(note.id, notebookIds) },
                onDismiss = { showManageNoteDialog = false; contextMenuNote = null }
            )
        }
    }

    if (showPermanentDeleteNotebookDialog) {
        contextMenuNotebook?.let { notebook ->
            ConfirmDeleteDialog(
                title = "Delete Permanently",
                message = "Permanently delete \"${notebook.name}\" and all its notes? This cannot be undone.",
                onConfirm = { viewModel.permanentlyDeleteNotebook(notebook.id) },
                onDismiss = { showPermanentDeleteNotebookDialog = false; contextMenuNotebook = null }
            )
        }
    }

    if (showPermanentDeleteFolderDialog) {
        contextMenuFolder?.let { folder ->
            ConfirmDeleteDialog(
                title = "Delete Permanently",
                message = "Permanently delete \"${folder.name}\" and all its contents? This cannot be undone.",
                onConfirm = { viewModel.permanentlyDeleteFolder(folder.id) },
                onDismiss = { showPermanentDeleteFolderDialog = false; contextMenuFolder = null }
            )
        }
    }

    if (showPermanentDeleteNoteDialog) {
        contextMenuNote?.let { note ->
            ConfirmDeleteDialog(
                title = "Delete Permanently",
                message = "Permanently delete \"${note.title}\"? This cannot be undone.",
                onConfirm = { viewModel.permanentlyDeleteNote(note.id) },
                onDismiss = { showPermanentDeleteNoteDialog = false; contextMenuNote = null }
            )
        }
    }

    if (showEmptyTrashDialog) {
        ConfirmDeleteDialog(
            title = "Empty Trash",
            message = "Permanently delete all items in Trash? This cannot be undone.",
            onConfirm = { viewModel.emptyTrash() },
            onDismiss = { showEmptyTrashDialog = false }
        )
    }

    if (showSearchDialog) {
        SearchDialog(
            searchResults = searchResults,
            isSearching = isSearching,
            onSearch = { viewModel.search(it) },
            onResultClick = { result ->
                showSearchDialog = false
                viewModel.clearSearch()
                result.notebookId?.let { onNotebookSelected(it, result.noteId) }
            },
            onDismiss = { showSearchDialog = false; viewModel.clearSearch() }
        )
    }
}
