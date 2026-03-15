package com.wyldsoft.notes.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.export.ExportAction
import com.wyldsoft.notes.export.ExportScope

/**
 * Dialog that lets the user choose export scope (single note vs. notebook)
 * and export action (save file vs. share).
 *
 * @param noteName          Title of the current note.
 * @param notebookName      Name of the notebook, or null if not in a notebook.
 * @param defaultScope      Pre-selected scope (use NOTEBOOK when triggered from notebook context menu).
 * @param onExport          Called when user taps Export with chosen scope and action.
 * @param onDismiss         Called when user dismisses without exporting.
 */
@Composable
fun ExportDialog(
    noteName: String,
    notebookName: String?,
    defaultScope: ExportScope = ExportScope.SINGLE_NOTE,
    onExport: (scope: ExportScope, action: ExportAction) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedScope by remember { mutableStateOf(defaultScope) }
    var selectedAction by remember { mutableStateOf(ExportAction.SHARE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export as PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Scope section (only shown if note belongs to a notebook)
                if (notebookName != null) {
                    Text("What to export:", style = MaterialTheme.typography.labelLarge)
                    ScopeOption(
                        label = "This note: \"$noteName\"",
                        selected = selectedScope == ExportScope.SINGLE_NOTE,
                        onClick = { selectedScope = ExportScope.SINGLE_NOTE }
                    )
                    ScopeOption(
                        label = "Entire notebook: \"$notebookName\"",
                        selected = selectedScope == ExportScope.NOTEBOOK,
                        onClick = { selectedScope = ExportScope.NOTEBOOK }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Action section
                Text("Export to:", style = MaterialTheme.typography.labelLarge)
                ScopeOption(
                    label = "Share",
                    selected = selectedAction == ExportAction.SHARE,
                    onClick = { selectedAction = ExportAction.SHARE }
                )
                ScopeOption(
                    label = "Save to file",
                    selected = selectedAction == ExportAction.SAVE_FILE,
                    onClick = { selectedAction = ExportAction.SAVE_FILE }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(selectedScope, selectedAction) }) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ScopeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}
