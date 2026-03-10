package com.wyldsoft.notes.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity

@Composable
fun MoveToFolderDialog(
    folders: List<FolderEntity>,
    excludeFolderId: String? = null,
    title: String = "Move to Folder",
    onMove: (FolderEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val available = folders.filter { it.id != excludeFolderId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (available.isEmpty()) {
                Text("No folders available.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(available) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMove(folder); onDismiss() }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MoveNoteDialog(
    notebooks: List<NotebookEntity>,
    folders: List<FolderEntity>,
    onMoveToNotebook: (NotebookEntity) -> Unit,
    onMoveToFolder: (FolderEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move Note") },
        text = {
            Column {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Notebooks") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Folders") })
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    if (tab == 0) {
                        if (notebooks.isEmpty()) {
                            item { Text("No notebooks available.", modifier = Modifier.padding(8.dp)) }
                        } else {
                            items(notebooks) { notebook ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onMoveToNotebook(notebook); onDismiss() }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Book, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(notebook.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    } else {
                        if (folders.isEmpty()) {
                            item { Text("No folders available.", modifier = Modifier.padding(8.dp)) }
                        } else {
                            items(folders) { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onMoveToFolder(folder); onDismiss() }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
