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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.home.components.BreadcrumbBar
import com.wyldsoft.notes.home.components.CreateItemDialog
import com.wyldsoft.notes.home.components.FolderCard
import com.wyldsoft.notes.home.components.NotebookCard
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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncUiState by syncViewModel.syncUiState.collectAsState()
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateNotebookDialog by remember { mutableStateOf(false) }

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
        // Breadcrumb bar
        BreadcrumbBar(
            breadcrumbs = uiState.breadcrumbs,
            onFolderClick = { folderId -> viewModel.navigateToFolder(folderId) }
        )

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
            IconButton(onClick = { showCreateFolderDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create folder"
                )
            }
        }

        // Folders row
        if (uiState.folders.isEmpty()) {
            Text(
                text = "No folders",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.folders) { folder ->
                    FolderCard(
                        folder = folder,
                        onClick = { viewModel.navigateToFolder(folder.id) }
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
            IconButton(onClick = { showCreateNotebookDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create notebook"
                )
            }
        }

        // Notebooks row
        if (uiState.notebooks.isEmpty()) {
            Text(
                text = "No notebooks",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.notebooks) { notebook ->
                    NotebookCard(
                        notebook = notebook,
                        onClick = { onOpenNotebook(notebook.id) }
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
}
