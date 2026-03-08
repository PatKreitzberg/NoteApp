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
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.presentation.viewmodel.HomeViewModel
import com.wyldsoft.notes.gestures.GestureSettingsRepository
import com.wyldsoft.notes.presentation.viewmodel.SyncViewModel
import com.wyldsoft.notes.settings.DisplaySettingsRepository
import com.wyldsoft.notes.ui.components.dialogs.AppSettingsDialog
import com.wyldsoft.notes.ui.components.dialogs.GoogleDriveDialog
import com.wyldsoft.notes.ui.components.dialogs.NotebookSettingsDialog

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
    val showCreateFolderDialog by viewModel.showCreateFolderDialog.collectAsState()
    val showCreateNotebookDialog by viewModel.showCreateNotebookDialog.collectAsState()
    var showAppSettingsDialog by remember { mutableStateOf(false) }
    var showGoogleDriveDialog by remember { mutableStateOf(false) }
    var selectedNotebook by remember { mutableStateOf<NotebookEntity?>(null) }
    var showSearchDialog by remember { mutableStateOf(false) }
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

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
        FolderRow(folders = subfolders, onFolderClick = { viewModel.navigateToFolder(it.id) })

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
            onNotebookLongClick = { selectedNotebook = it }
        )
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

    selectedNotebook?.let { notebook ->
        NotebookSettingsDialog(
            notebookName = notebook.name,
            onRename = { newName -> viewModel.renameNotebook(notebook.id, newName); selectedNotebook = null },
            onDismiss = { selectedNotebook = null }
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
