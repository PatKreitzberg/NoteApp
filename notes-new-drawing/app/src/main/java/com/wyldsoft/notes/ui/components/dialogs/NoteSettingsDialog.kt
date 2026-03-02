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
    isPaginationEnabled: Boolean,
    currentPaperSize: PaperSize,
    currentPaperTemplate: PaperTemplate,
    onPaginationToggle: (Boolean) -> Unit,
    onPaperSizeChange: (PaperSize) -> Unit,
    onPaperTemplateChange: (PaperTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    var paginationEnabled by remember { mutableStateOf(isPaginationEnabled) }
    var selectedPaperSize by remember { mutableStateOf(currentPaperSize) }
    var selectedTemplate by remember { mutableStateOf(currentPaperTemplate) }
    
    SettingsDialogShell(title = "Note Settings", onDismiss = onDismiss) {
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

        // Paper size dropdown (only visible when pagination is enabled)
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

        // Paper template dropdown
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
    }
}