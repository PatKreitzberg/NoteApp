package com.wyldsoft.notes.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemCard(
    name: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = true,
    isInTrash: Boolean = false,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .width(120.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (longPressEnabled) menuExpanded = true
                    }
                ),
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colors.primary
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            if (isInTrash) {
                DropdownMenuItem(onClick = {
                    menuExpanded = false
                    onRestore?.invoke()
                }) {
                    Text("Restore")
                }
            } else {
                DropdownMenuItem(onClick = {
                    menuExpanded = false
                    onRename?.invoke()
                }) {
                    Text("Rename")
                }
                DropdownMenuItem(onClick = {
                    menuExpanded = false
                    onMove?.invoke()
                }) {
                    Text("Move")
                }
                DropdownMenuItem(onClick = {
                    menuExpanded = false
                    onDelete?.invoke()
                }) {
                    Text("Delete")
                }
            }
        }
    }
}
