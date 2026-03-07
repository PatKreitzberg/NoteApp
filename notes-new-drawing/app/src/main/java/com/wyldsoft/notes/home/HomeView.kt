package com.wyldsoft.notes.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.presentation.viewmodel.HomeViewModel
import com.wyldsoft.notes.presentation.viewmodel.SearchResult
import com.wyldsoft.notes.gestures.GestureSettingsRepository
import com.wyldsoft.notes.presentation.viewmodel.SyncViewModel
import com.wyldsoft.notes.settings.DisplaySettingsRepository
import com.wyldsoft.notes.ui.components.dialogs.AppSettingsDialog
import com.wyldsoft.notes.ui.components.dialogs.GoogleDriveDialog
import com.wyldsoft.notes.ui.components.dialogs.NotebookSettingsDialog
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@Composable
fun HomeView(
    viewModel: HomeViewModel,
    gestureSettingsRepository: GestureSettingsRepository,
    displaySettingsRepository: DisplaySettingsRepository,
    signInLauncher: ActivityResultLauncher<Intent>,
    signInError: State<String?>,
    syncViewModel: SyncViewModel,
    onNotebookSelected: (String, String) -> Unit // notebookId, noteId
) {
    val currentFolder by viewModel.currentFolder.collectAsState()
    val folderPath by viewModel.folderPath.collectAsState()
    val subfolders by viewModel.subfolders.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()
    val showCreateFolderDialog by viewModel.showCreateFolderDialog.collectAsState()
    val showCreateNotebookDialog by viewModel.showCreateNotebookDialog.collectAsState()
    var showAppSettingsDialog by remember { mutableStateOf(false) }
    var showGoogleDriveDialog by remember { mutableStateOf(false) }
    var selectedNotebook by remember { mutableStateOf<NotebookEntity?>(null) }
    var showSearchDialog by remember { mutableStateOf(false) }
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()



    // Create notebook dialog
    if (showCreateNotebookDialog) {
        Log.d("HomeView", "Show create notebook dialog if statement")
        CreateItemDialog(
            title = "Create Notebook",
            placeholder = "Notebook name",
            onConfirm = { name ->
                viewModel.createNotebook(name)
            },
            onDismiss = {
                Log.d("HomeView", "Hiding create notebook dialog")
                viewModel.hideCreateNotebookDialog()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top bar with settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Breadcrumb navigation
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BreadcrumbBar(
                    folderPath = folderPath,
                    onFolderClick = { folder ->
                        viewModel.navigateToFolder(folder.id)
                    }
                )
            }
            
            // Search icon
            IconButton(onClick = { showSearchDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Notes"
                )
            }

            // Settings icon
            IconButton(onClick = { showAppSettingsDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "App Settings"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Folders section
        SectionHeader(
            title = "Folders",
            onAddClick = { viewModel.showCreateFolderDialog() }
        )
        
        FolderRow(
            folders = subfolders,
            onFolderClick = { folder ->
                viewModel.navigateToFolder(folder.id)
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Notebooks section
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
                selectedNotebook = notebook
            }
        )
    }
    
    // Create folder dialog
    if (showCreateFolderDialog) {
        CreateItemDialog(
            title = "Create Folder",
            placeholder = "Folder name",
            onConfirm = { name ->
                viewModel.createFolder(name)
            },
            onDismiss = {
                viewModel.hideCreateFolderDialog()
            }
        )
    }


    
    // App settings dialog
    if (showAppSettingsDialog) {
        AppSettingsDialog(
            gestureSettingsRepository = gestureSettingsRepository,
            displaySettingsRepository = displaySettingsRepository,
            onDismiss = { showAppSettingsDialog = false },
            onOpenGoogleDrive = {
                showAppSettingsDialog = false
                showGoogleDriveDialog = true
            }
        )
    }

    // Google Drive dialog
    if (showGoogleDriveDialog) {
        GoogleDriveDialog(
            signInLauncher = signInLauncher,
            signInError = signInError,
            syncViewModel = syncViewModel,
            onDismiss = { showGoogleDriveDialog = false }
        )
    }
    
    // Notebook settings dialog
    selectedNotebook?.let { notebook ->
        NotebookSettingsDialog(
            notebookName = notebook.name,
            onRename = { newName ->
                viewModel.renameNotebook(notebook.id, newName)
                selectedNotebook = null
            },
            onDismiss = {
                selectedNotebook = null
            }
        )
    }

    // Search dialog
    if (showSearchDialog) {
        SearchDialog(
            searchResults = searchResults,
            isSearching = isSearching,
            onSearch = { query -> viewModel.search(query) },
            onResultClick = { result ->
                showSearchDialog = false
                viewModel.clearSearch()
                val notebookId = result.notebookId
                if (notebookId != null) {
                    onNotebookSelected(notebookId, result.noteId)
                }
            },
            onDismiss = {
                showSearchDialog = false
                viewModel.clearSearch()
            }
        )
    }
}

@Composable
fun BreadcrumbBar(
    folderPath: List<FolderEntity>,
    onFolderClick: (FolderEntity) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        folderPath.forEachIndexed { index, folder ->
            Text(
                text = folder.name,
                modifier = Modifier.clickable { onFolderClick(folder) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            if (index < folderPath.size - 1) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onAddClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall
        )
        
        IconButton(onClick = onAddClick) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add $title"
            )
        }
    }
}

@Composable
fun FolderRow(
    folders: List<FolderEntity>,
    onFolderClick: (FolderEntity) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        folders.forEach { folder ->
            FolderItem(
                folder = folder,
                onClick = { onFolderClick(folder) }
            )
        }
    }
}

@Composable
fun FolderItem(
    folder: FolderEntity,
    onClick: () -> Unit
) {
    ItemCard(
        name = folder.name,
        icon = Icons.Default.Folder,
        iconTint = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
fun NotebookRow(
    notebooks: List<NotebookEntity>,
    viewModel: HomeViewModel,
    onNotebookSelected: (String, String) -> Unit,
    onNotebookLongClick: (NotebookEntity) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        notebooks.forEach { notebook ->
            NotebookItem(
                notebook = notebook,
                viewModel = viewModel,
                onNotebookSelected = onNotebookSelected,
                onLongClick = { onNotebookLongClick(notebook) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotebookItem(
    notebook: NotebookEntity,
    viewModel: HomeViewModel,
    onNotebookSelected: (String, String) -> Unit,
    onLongClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    ItemCard(
        name = notebook.name,
        icon = Icons.Default.Book,
        iconTint = MaterialTheme.colorScheme.secondary,
        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.combinedClickable(
            onClick = {
                coroutineScope.launch {
                    val noteId = viewModel.getFirstNoteInNotebook(notebook.id)
                    noteId?.let {
                        onNotebookSelected(notebook.id, it)
                    }
                }
            },
            onLongClick = onLongClick
        )
    )
}

@Composable
fun ItemCard(
    name: String,
    icon: ImageVector,
    iconTint: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = name,
            modifier = Modifier.size(48.dp),
            tint = iconTint
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SearchDialog(
    searchResults: List<SearchResult>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search Notes") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearch(it)
                    },
                    label = { Text("Search handwritten text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else if (query.isNotBlank() && searchResults.isEmpty()) {
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        searchResults.forEach { result ->
                            SearchResultItem(
                                result = result,
                                onClick = { onResultClick(result) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            text = result.noteTitle,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = result.matchedText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CreateItemDialog(
    title: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(placeholder) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}