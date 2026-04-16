package com.wyldsoft.notes.home.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wyldsoft.notes.data.database.entities.PenProfileSetEntity

/**
 * Dialog for managing pen profile sets in HomeView.
 * Lists all sets with rename/delete actions, and a button to save the
 * current editor profiles as a new named set.
 */
@Composable
fun PenProfileSetsDialog(
    sets: List<PenProfileSetEntity>,
    onSaveCurrentAsNewSet: (name: String) -> Unit,
    onRenameSet: (id: String, newName: String) -> Unit,
    onDeleteSet: (id: String) -> Unit,
    onDismiss: () -> Unit
) {
    var showNewSetInput by remember { mutableStateOf(false) }
    var newSetName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<PenProfileSetEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Black, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Pen Profile Sets",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Switch sets instantly from the toolbar. Changes in the editor apply to the active set.",
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Divider(color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))

                if (sets.isEmpty()) {
                    Text(
                        text = "No sets saved yet.",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(sets) { set ->
                            PenProfileSetRow(
                                set = set,
                                isRenaming = renameTarget?.id == set.id,
                                renameText = if (renameTarget?.id == set.id) renameText else set.name,
                                onRenameTextChange = { renameText = it },
                                onStartRename = {
                                    renameTarget = set
                                    renameText = set.name
                                },
                                onConfirmRename = {
                                    val trimmed = renameText.trim()
                                    if (trimmed.isNotBlank()) {
                                        onRenameSet(set.id, trimmed)
                                    }
                                    renameTarget = null
                                },
                                onCancelRename = { renameTarget = null },
                                onDelete = { onDeleteSet(set.id) }
                            )
                            Divider()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))

                // Save current profiles as new set
                if (showNewSetInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSetName,
                            onValueChange = { newSetName = it },
                            placeholder = { Text("Set name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val trimmed = newSetName.trim()
                                if (trimmed.isNotBlank()) {
                                    onSaveCurrentAsNewSet(trimmed)
                                    newSetName = ""
                                    showNewSetInput = false
                                }
                            }
                        ) { Text("Save") }
                        TextButton(
                            onClick = {
                                showNewSetInput = false
                                newSetName = ""
                            }
                        ) { Text("Cancel") }
                    }
                } else {
                    TextButton(
                        onClick = { showNewSetInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save current profiles as new set")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun PenProfileSetRow(
    set: PenProfileSetEntity,
    isRenaming: Boolean,
    renameText: String,
    onRenameTextChange: (String) -> Unit,
    onStartRename: () -> Unit,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRenaming) {
            OutlinedTextField(
                value = renameText,
                onValueChange = onRenameTextChange,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onConfirmRename) { Text("OK", fontSize = 12.sp) }
            TextButton(onClick = onCancelRename) { Text("✕", fontSize = 12.sp) }
        } else {
            Text(
                text = set.name,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onStartRename, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename",
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
