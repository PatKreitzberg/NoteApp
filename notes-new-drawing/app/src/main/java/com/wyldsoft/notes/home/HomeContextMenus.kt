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
        DropdownMenuItem(text = { Text("Move") }, onClick = onMove)
        DropdownMenuItem(text = { Text("Rename") }, onClick = onRename)
        DropdownMenuItem(text = { Text("Move to Trash") }, onClick = onDelete)
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
        DropdownMenuItem(text = { Text("Move") }, onClick = onMove)
        DropdownMenuItem(text = { Text("Rename") }, onClick = onRename)
        DropdownMenuItem(text = { Text("Move to Trash") }, onClick = onDelete)
    }
}

@Composable
fun TrashFolderContextMenu(
    onEmptyTrash: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Empty Trash") }, onClick = onEmptyTrash)
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
        DropdownMenuItem(text = { Text("Move") }, onClick = onMove)
        DropdownMenuItem(text = { Text("Rename") }, onClick = onRename)
        DropdownMenuItem(text = { Text("Manage Notebooks") }, onClick = onManage)
        DropdownMenuItem(text = { Text("Move to Trash") }, onClick = onDelete)
    }
}

@Composable
fun TrashItemNotebookContextMenu(
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Restore") }, onClick = onRestore)
        DropdownMenuItem(text = { Text("Delete Permanently") }, onClick = onDeletePermanently)
    }
}

@Composable
fun TrashItemFolderContextMenu(
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Restore") }, onClick = onRestore)
        DropdownMenuItem(text = { Text("Delete Permanently") }, onClick = onDeletePermanently)
    }
}

@Composable
fun TrashItemNoteContextMenu(
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Restore") }, onClick = onRestore)
        DropdownMenuItem(text = { Text("Delete Permanently") }, onClick = onDeletePermanently)
    }
}
