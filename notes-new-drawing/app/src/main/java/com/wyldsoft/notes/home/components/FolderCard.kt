package com.wyldsoft.notes.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wyldsoft.notes.data.database.entities.FolderEntity

@Composable
fun FolderCard(
    folder: FolderEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isTrashFolder: Boolean = false,
    isInTrash: Boolean = false,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null
) = ItemCard(
    name = folder.name,
    icon = if (isTrashFolder) Icons.Default.Delete else Icons.Default.Folder,
    onClick = onClick,
    modifier = modifier,
    longPressEnabled = !isTrashFolder,
    isInTrash = isInTrash,
    onRename = onRename,
    onDelete = onDelete,
    onMove = onMove,
    onRestore = onRestore
)
