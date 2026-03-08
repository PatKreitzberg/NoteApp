package com.wyldsoft.notes.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.presentation.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

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
fun SectionHeader(title: String, onAddClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        IconButton(onClick = onAddClick) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add $title")
        }
    }
}

@Composable
fun FolderRow(folders: List<FolderEntity>, onFolderClick: (FolderEntity) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        folders.forEach { folder ->
            FolderItem(folder = folder, onClick = { onFolderClick(folder) })
        }
    }
}

@Composable
fun FolderItem(folder: FolderEntity, onClick: () -> Unit) {
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
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                    noteId?.let { onNotebookSelected(notebook.id, it) }
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
        Icon(imageVector = icon, contentDescription = name, modifier = Modifier.size(48.dp), tint = iconTint)
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
