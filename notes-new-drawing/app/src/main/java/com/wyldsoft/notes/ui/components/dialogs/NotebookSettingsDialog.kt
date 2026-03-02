package com.wyldsoft.notes.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NotebookSettingsDialog(
    notebookName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(notebookName) }

    SettingsDialogShell(
        title = "Notebook Settings",
        onDismiss = onDismiss,
        bottomButtons = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newName.isNotBlank() && newName != notebookName) {
                        onRename(newName)
                        onDismiss()
                    }
                },
                enabled = newName.isNotBlank() && newName != notebookName
            ) {
                Text("Save")
            }
        }
    ) {
        // Rename field
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Notebook Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}