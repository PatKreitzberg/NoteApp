package com.wyldsoft.notes.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onOpenNotebookAtNote: (notebookId: String, noteId: String, scrollY: Float) -> Unit = { _, _, _ -> },
    onImportPdf: () -> Unit,
    onShareNotebook: (notebookId: String) -> Unit = {},
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
    val searchResults by viewModel.searchResults.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateNotebookDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Rename state
    var renameFolderTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var renameNotebookTarget by remember { mutableStateOf<NotebookEntity?>(null) }

    // Move state
    var moveFolderTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var moveNotebookTarget by remember { mutableStateOf<NotebookEntity?>(null) }

    val notebookExportState by viewModel.notebookExportState.collectAsState()

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
        // Top bar: breadcrumbs + search + settings buttons
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
            IconButton(onClick = {
                isSearchActive = true
                searchQuery = ""
                viewModel.search("")
            }) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
            }
            IconButton(onClick = { showSettingsDialog = true }) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        // Search bar (shown when search is active)
        if (isSearchActive) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { q ->
                        searchQuery = q
                        viewModel.search(q)
                    },
                    placeholder = { Text("Search all notes...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search(searchQuery) })
                )
                IconButton(onClick = {
                    isSearchActive = false
                    searchQuery = ""
                    viewModel.search("")
                }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close search")
                }
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

        // Show search results when search is active with a query; otherwise show folders/notebooks
        if (isSearchActive && searchQuery.isNotBlank()) {
            if (searchResults.isEmpty()) {
                Text(
                    text = "No results for \"$searchQuery\"",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(searchResults) { result ->
                        HomeSearchResultItem(
                            result = result,
                            query = searchQuery,
                            onClick = {
                                onOpenNotebookAtNote(result.notebookId, result.noteId, result.boundingTop)
                            }
                        )
                        Divider()
                    }
                }
            }
        } else {

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
                Row() {
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
                        onRestore = { viewModel.restoreNotebookFromTrash(notebook.id) },
                        onShare = if (!isInTrash) {{ onShareNotebook(notebook.id) }} else null
                    )
                }
            }
        }
        } // end else (not showing search results)
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

    if (notebookExportState is NotebookExportState.InProgress) {
        Dialog(onDismissRequest = {}) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Exporting notebook\u2026")
                }
            }
        }
    }
}

@Composable
private fun HomeSearchResultItem(
    result: HomeSearchResult,
    query: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Text(
            text = result.notebookName,
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        val snippet = buildSnippet(result.matchText, query)
        Text(
            text = snippet,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
        )
    }
}

private fun buildSnippet(matchText: String, query: String): androidx.compose.ui.text.AnnotatedString {
    val lower = matchText.lowercase()
    val queryLower = query.lowercase()
    val idx = lower.indexOf(queryLower)
    return buildAnnotatedString {
        if (idx < 0) {
            append(matchText.take(80))
        } else {
            val start = maxOf(0, idx - 20)
            val end = minOf(matchText.length, idx + query.length + 40)
            if (start > 0) append("...")
            append(matchText.substring(start, idx))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(matchText.substring(idx, idx + query.length))
            }
            append(matchText.substring(idx + query.length, end))
            if (end < matchText.length) append("...")
        }
    }
}
