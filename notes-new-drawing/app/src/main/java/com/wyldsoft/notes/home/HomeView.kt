package com.wyldsoft.notes.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.gestures.GestureAction
import com.wyldsoft.notes.home.components.BreadcrumbBar
import com.wyldsoft.notes.home.components.CreateItemDialog
import com.wyldsoft.notes.home.components.FolderCard
import com.wyldsoft.notes.home.components.HomeSettingsDialog
import com.wyldsoft.notes.home.components.MoveItemDialog
import com.wyldsoft.notes.home.components.NotebookCard
import com.wyldsoft.notes.home.components.RenameDialog
import com.wyldsoft.notes.home.components.SyncBar
import com.wyldsoft.notes.sync.SyncUiState
import com.wyldsoft.notes.sync.SyncViewModel

@Composable
fun HomeView(
    viewModel: HomeViewModel,
    syncViewModel: SyncViewModel,
    isSignedIn: Boolean,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onOpenNotebook: (notebookId: String) -> Unit,
    onImportPdf: () -> Unit,
    defaultPaginationEnabled: Boolean,
    onDefaultPaginationChanged: (Boolean) -> Unit,
    gestureMappings: Map<String, GestureAction>,
    onGestureMappingsChanged: (Map<String, GestureAction>) -> Unit,
    scribbleToEraseEnabled: Boolean,
    onScribbleToEraseToggle: (Boolean) -> Unit,
    circleToSelectEnabled: Boolean,
    onCircleToSelectToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncUiState by syncViewModel.syncUiState.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    val isInTrash = uiState.currentFolderId == FolderEntity.TRASH_ID

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateNotebookDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Rename state
    var renameFolderTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var renameNotebookTarget by remember { mutableStateOf<NotebookEntity?>(null) }

    // Move state
    var moveFolderTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var moveNotebookTarget by remember { mutableStateOf<NotebookEntity?>(null) }

    if (uiState.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top bar: breadcrumbs + settings button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BreadcrumbBar(
                breadcrumbs = uiState.breadcrumbs,
                onFolderClick = { folderId -> viewModel.navigateToFolder(folderId) },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showSettingsDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        }

        // Sync status bar
        SyncBar(
            isSignedIn = isSignedIn,
            syncUiState = syncUiState,
            onSignInClick = onSignInClick,
            onSignOutClick = onSignOutClick,
            onSyncNowClick = { syncViewModel.triggerSync() }
        )
        Divider()

        // Folders section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Folders",
                style = MaterialTheme.typography.h6
            )
            if (!isInTrash) {
                IconButton(onClick = { showCreateFolderDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create folder"
                    )
                }
            }
        }

        // Folders row
        val hasTrash = uiState.currentFolderId == FolderEntity.ROOT_ID
        val hasFolders = uiState.folders.isNotEmpty() || hasTrash
        if (!hasFolders) {
            Text(
                text = if (isInTrash) "Trash is empty" else "No folders",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Trash folder shown first at root level
                if (hasTrash) {
                    item {
                        val trashFolder = FolderEntity(
                            id = FolderEntity.TRASH_ID,
                            name = "Trash",
                            parentFolderId = FolderEntity.ROOT_ID
                        )
                        FolderCard(
                            folder = trashFolder,
                            onClick = { viewModel.navigateToFolder(FolderEntity.TRASH_ID) },
                            isTrashFolder = true
                        )
                    }
                }
                items(uiState.folders) { folder ->
                    FolderCard(
                        folder = folder,
                        onClick = { viewModel.navigateToFolder(folder.id) },
                        isInTrash = isInTrash,
                        onRename = { renameFolderTarget = folder },
                        onDelete = { viewModel.moveFolderToTrash(folder.id) },
                        onMove = { moveFolderTarget = folder },
                        onRestore = { viewModel.restoreFolderFromTrash(folder.id) }
                    )
                }
            }
        }

        // Notebooks section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Notebooks",
                style = MaterialTheme.typography.h6
            )
            if (!isInTrash) {
                IconButton(onClick = { onImportPdf() }) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Import PDF"
                    )
                }
                IconButton(onClick = { showCreateNotebookDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create notebook"
                    )
                }
            }
        }

        // Notebooks row
        if (uiState.notebooks.isEmpty()) {
            if (!isInTrash || uiState.folders.isNotEmpty()) {
                Text(
                    text = "No notebooks",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.notebooks) { notebook ->
                    NotebookCard(
                        notebook = notebook,
                        onClick = { if (!isInTrash) onOpenNotebook(notebook.id) },
                        isInTrash = isInTrash,
                        onRename = { renameNotebookTarget = notebook },
                        onDelete = { viewModel.moveNotebookToTrash(notebook.id) },
                        onMove = { moveNotebookTarget = notebook },
                        onRestore = { viewModel.restoreNotebookFromTrash(notebook.id) }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showCreateFolderDialog) {
        CreateItemDialog(
            title = "New Folder",
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    if (showCreateNotebookDialog) {
        CreateItemDialog(
            title = "New Notebook",
            onConfirm = { name ->
                viewModel.createNotebook(name)
                showCreateNotebookDialog = false
            },
            onDismiss = { showCreateNotebookDialog = false }
        )
    }

    if (showSettingsDialog) {
        HomeSettingsDialog(
            defaultPaginationEnabled = defaultPaginationEnabled,
            onDefaultPaginationChanged = onDefaultPaginationChanged,
            gestureMappings = gestureMappings,
            onGestureMappingsChanged = onGestureMappingsChanged,
            scribbleToEraseEnabled = scribbleToEraseEnabled,
            onScribbleToEraseToggle = onScribbleToEraseToggle,
            circleToSelectEnabled = circleToSelectEnabled,
            onCircleToSelectToggle = onCircleToSelectToggle,
            onDismiss = { showSettingsDialog = false }
        )
    }

    renameFolderTarget?.let { folder ->
        RenameDialog(
            title = "Rename Folder",
            currentName = folder.name,
            onConfirm = { newName ->
                viewModel.renameFolder(folder.id, newName)
                renameFolderTarget = null
            },
            onDismiss = { renameFolderTarget = null }
        )
    }

    renameNotebookTarget?.let { notebook ->
        RenameDialog(
            title = "Rename Notebook",
            currentName = notebook.name,
            onConfirm = { newName ->
                viewModel.renameNotebook(notebook.id, newName)
                renameNotebookTarget = null
            },
            onDismiss = { renameNotebookTarget = null }
        )
    }

    moveFolderTarget?.let { folder ->
        MoveItemDialog(
            availableFolders = allFolders.filter { it.id != folder.id },
            currentParentId = folder.parentFolderId ?: FolderEntity.ROOT_ID,
            onFolderSelected = { destId ->
                viewModel.moveFolder(folder.id, destId)
                moveFolderTarget = null
            },
            onDismiss = { moveFolderTarget = null }
        )
    }

    moveNotebookTarget?.let { notebook ->
        MoveItemDialog(
            availableFolders = allFolders,
            currentParentId = notebook.folderId,
            onFolderSelected = { destId ->
                viewModel.moveNotebook(notebook.id, destId)
                moveNotebookTarget = null
            },
            onDismiss = { moveNotebookTarget = null }
        )
    }
}
