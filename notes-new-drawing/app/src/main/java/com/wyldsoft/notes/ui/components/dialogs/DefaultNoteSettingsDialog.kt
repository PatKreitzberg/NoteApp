package com.wyldsoft.notes.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.domain.models.PaperSize
import com.wyldsoft.notes.domain.models.PaperTemplate
import com.wyldsoft.notes.settings.DefaultNoteSettingsRepository

@Composable
fun DefaultNoteSettingsDialog(
    repository: DefaultNoteSettingsRepository,
    onDismiss: () -> Unit
) {
    val isPaginationEnabled by repository.isPaginationEnabled.collectAsState()
    val currentPaperSize by repository.paperSize.collectAsState()
    val currentPaperTemplate by repository.paperTemplate.collectAsState()

    var paginationEnabled by remember { mutableStateOf(isPaginationEnabled) }
    var selectedPaperSize by remember { mutableStateOf(currentPaperSize) }
    var selectedTemplate by remember { mutableStateOf(currentPaperTemplate) }

    SettingsDialogShell(title = "Default Note Settings", onDismiss = {
        repository.setPaginationEnabled(paginationEnabled)
        repository.setPaperSize(selectedPaperSize)
        repository.setPaperTemplate(selectedTemplate)
        onDismiss()
    }) {
        // Pagination toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enable Pagination")
            Switch(
                checked = paginationEnabled,
                onCheckedChange = { paginationEnabled = it }
            )
        }

        if (paginationEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsDropdown(
                label = "Paper Size",
                items = PaperSize.entries,
                selectedItem = selectedPaperSize,
                displayName = { it.displayName },
                onItemSelected = { selectedPaperSize = it }
            )
        }

        SettingsDropdown(
            label = "Paper Template",
            items = PaperTemplate.entries,
            selectedItem = selectedTemplate,
            displayName = { it.displayName },
            onItemSelected = { selectedTemplate = it }
        )
    }
}
