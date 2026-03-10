package com.wyldsoft.notes.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.data.database.entities.NotebookEntity

@Composable
fun ManageNoteDialog(
    notebooks: List<NotebookEntity>,
    checkedNotebookIds: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val checked = remember(checkedNotebookIds) { checkedNotebookIds.toMutableStateList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Notebooks") },
        text = {
            if (notebooks.isEmpty()) {
                Text("No notebooks available. Create a notebook first.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(notebooks) { notebook ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = notebook.id in checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) checked.add(notebook.id)
                                    else checked.remove(notebook.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(notebook.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(checked.toList()); onDismiss() }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
