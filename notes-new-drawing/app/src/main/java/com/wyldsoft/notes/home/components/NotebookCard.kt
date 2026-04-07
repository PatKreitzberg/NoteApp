package com.wyldsoft.notes.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wyldsoft.notes.data.database.entities.NotebookEntity

@Composable
fun NotebookCard(
    notebook: NotebookEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isInTrash: Boolean = false,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null
) = ItemCard(
    name = notebook.name,
    icon = Icons.AutoMirrored.Filled.MenuBook,
    onClick = onClick,
    modifier = modifier,
    isInTrash = isInTrash,
    onRename = onRename,
    onDelete = onDelete,
    onMove = onMove,
    onRestore = onRestore
)
