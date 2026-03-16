package com.wyldsoft.notes.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.domain.models.PaperSize
import com.wyldsoft.notes.domain.models.PaperTemplate

@Composable
fun NoteSettingsDialog(
    noteName: String,
    isPaginationEnabled: Boolean,
    currentPaperSize: PaperSize,
    currentPaperTemplate: PaperTemplate,
    scribbleToEraseEnabled: Boolean,
    circleToSelectEnabled: Boolean,
    onRenameNote: (String) -> Unit,
    onPaginationToggle: (Boolean) -> Unit,
    onPaperSizeChange: (PaperSize) -> Unit,
    onPaperTemplateChange: (PaperTemplate) -> Unit,
    onScribbleToEraseToggle: (Boolean) -> Unit,
    onCircleToSelectToggle: (Boolean) -> Unit,
    onManageNotebooks: () -> Unit,
    onExport: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    var nameField by remember { mutableStateOf(noteName) }
    var paginationEnabled by remember { mutableStateOf(isPaginationEnabled) }
    var selectedPaperSize by remember { mutableStateOf(currentPaperSize) }
    var selectedTemplate by remember { mutableStateOf(currentPaperTemplate) }

    SettingsDialogShell(title = "Note Settings", onDismiss = onDismiss) {
        // Note name
        OutlinedTextField(
            value = nameField,
            onValueChange = { nameField = it },
            label = { Text("Note Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (nameField != noteName && nameField.isNotBlank()) {
                    TextButton(onClick = { onRenameNote(nameField) }) { Text("Save") }
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Manage notebooks button
        OutlinedButton(
            onClick = onManageNotebooks,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manage Notebooks")
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Export button
        OutlinedButton(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export as PDF")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pagination toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enable Pagination")
            Switch(
                checked = paginationEnabled,
                onCheckedChange = {
                    paginationEnabled = it
                    onPaginationToggle(it)
                }
            )
        }

        if (paginationEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsDropdown(
                label = "Paper Size",
                items = PaperSize.entries,
                selectedItem = selectedPaperSize,
                displayName = { it.displayName },
                onItemSelected = { paperSize ->
                    selectedPaperSize = paperSize
                    onPaperSizeChange(paperSize)
                }
            )
        }

        SettingsDropdown(
            label = "Paper Template",
            items = PaperTemplate.entries,
            selectedItem = selectedTemplate,
            displayName = { it.displayName },
            onItemSelected = { template ->
                selectedTemplate = template
                onPaperTemplateChange(template)
            }
        )

        Divider()

        // Scribble-to-erase toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Scribble to erase")
            Switch(
                checked = scribbleToEraseEnabled,
                onCheckedChange = onScribbleToEraseToggle
            )
        }

        // Circle-to-select toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Circle to select")
            Switch(
                checked = circleToSelectEnabled,
                onCheckedChange = onCircleToSelectToggle
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // App Settings button
        OutlinedButton(
            onClick = onOpenAppSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("App Settings")
        }
    }
}
