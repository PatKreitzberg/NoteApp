package com.wyldsoft.notes.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wyldsoft.notes.domain.models.PaperSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSettingsDialog(
    isPaginationEnabled: Boolean,
    currentPaperSize: PaperSize,
    onPaginationToggle: (Boolean) -> Unit,
    onPaperSizeChange: (PaperSize) -> Unit,
    onDismiss: () -> Unit
) {
    var paginationEnabled by remember { mutableStateOf(isPaginationEnabled) }
    var selectedPaperSize by remember { mutableStateOf(currentPaperSize) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Note Settings",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Divider()
                
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
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Paper Size",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedPaperSize.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                PaperSize.values().forEach { paperSize ->
                                    DropdownMenuItem(
                                        text = { Text(paperSize.displayName) },
                                        onClick = {
                                            selectedPaperSize = paperSize
                                            onPaperSizeChange(paperSize)
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}