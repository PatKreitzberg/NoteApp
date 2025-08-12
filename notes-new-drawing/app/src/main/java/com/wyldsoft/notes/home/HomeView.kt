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
import com.wyldsoft.notes.ui.components.dialogs.AppSettingsDialog
import com.wyldsoft.notes.ui.components.dialogs.NotebookSettingsDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.wyldsoft.notes.data.repository.FolderRepository
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.data.repository.NotebookRepository

@Composable
fun HomeView(
    noteRepository: NoteRepository,
    notebookRepository: NotebookRepository,
    folderRepository: FolderRepository,
    onNotebookSelected: (String, String) -> Unit // notebookId, noteId
) {
    val viewModel = HomeViewModel(noteRepository, notebookRepository, folderRepository)
    
    val currentFolder by viewModel.currentFolder.collectAsState()
    val folderPath by viewModel.folderPath.collectAsState()
    val subfolders by viewModel.subfolders.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()
    val showCreateFolderDialog by viewModel.showCreateFolderDialog.collectAsState()
    val showCreateNotebookDialog by viewModel.showCreateNotebookDialog.collectAsState()
    var showAppSettingsDialog by remember { mutableStateOf(false) }
    var selectedNotebook by remember { mutableStateOf<NotebookEntity?>(null) }
    
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
                viewModel.hideCreateNotebookDialog()
            }
        )
    }
    
    // App settings dialog
    if (showAppSettingsDialog) {
        AppSettingsDialog(
            onDismiss = { showAppSettingsDialog = false }
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
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = folder.name,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
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
    
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .combinedClickable(
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Book,
            contentDescription = notebook.name,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = notebook.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
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