package com.wyldsoft.notes.home

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun NotebookContextMenu(
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Move") }, onClick = { onDismiss(); onMove() })
        DropdownMenuItem(text = { Text("Rename") }, onClick = { onDismiss(); onRename() })
        DropdownMenuItem(text = { Text("Delete") }, onClick = { onDismiss(); onDelete() })
    }
}

@Composable
fun FolderContextMenu(
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Move") }, onClick = { onDismiss(); onMove() })
        DropdownMenuItem(text = { Text("Rename") }, onClick = { onDismiss(); onRename() })
        DropdownMenuItem(text = { Text("Delete") }, onClick = { onDismiss(); onDelete() })
    }
}

@Composable
fun NoteContextMenu(
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Move") }, onClick = { onDismiss(); onMove() })
        DropdownMenuItem(text = { Text("Rename") }, onClick = { onDismiss(); onRename() })
        DropdownMenuItem(text = { Text("Manage Notebooks") }, onClick = { onDismiss(); onManage() })
        DropdownMenuItem(text = { Text("Delete") }, onClick = { onDismiss(); onDelete() })
    }
}
