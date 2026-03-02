package com.wyldsoft.notes.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.gestures.GestureAction
import com.wyldsoft.notes.gestures.GestureMapping
import com.wyldsoft.notes.gestures.GestureSettingsRepository
import com.wyldsoft.notes.gestures.GestureType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    gestureSettingsRepository: GestureSettingsRepository,
    onDismiss: () -> Unit
) {
    val savedMappings by gestureSettingsRepository.mappings.collectAsState()
    var mappings by remember { mutableStateOf(savedMappings) }

    val saveAndDismiss = {
        gestureSettingsRepository.saveMappings(mappings)
        onDismiss()
    }

    SettingsDialogShell(
        title = "App Settings",
        onDismiss = saveAndDismiss,
        scrollable = true
    ) {
        Text(
            text = "Gestures",
            style = MaterialTheme.typography.titleMedium
        )

        val usedGestures = mappings.map { it.gesture }.toSet()

        mappings.forEachIndexed { index, mapping ->
            GestureMappingRow(
                mapping = mapping,
                usedGestures = usedGestures,
                onGestureChange = { newGesture ->
                    mappings = mappings.toMutableList().apply {
                        this[index] = mapping.copy(gesture = newGesture)
                    }
                },
                onActionChange = { newAction ->
                    mappings = mappings.toMutableList().apply {
                        this[index] = mapping.copy(action = newAction)
                    }
                },
                onRemove = {
                    mappings = mappings.toMutableList().apply {
                        removeAt(index)
                    }
                }
            )
        }

        val availableGestures = GestureType.entries.filter { it !in usedGestures }
        if (availableGestures.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    mappings = mappings + GestureMapping(
                        availableGestures.first(),
                        GestureAction.NONE
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Gesture")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GestureMappingRow(
    mapping: GestureMapping,
    usedGestures: Set<GestureType>,
    onGestureChange: (GestureType) -> Unit,
    onActionChange: (GestureAction) -> Unit,
    onRemove: () -> Unit
) {
    var gestureExpanded by remember { mutableStateOf(false) }
    var actionExpanded by remember { mutableStateOf(false) }

    // Available gestures: current gesture + any unused ones
    val availableGestures = GestureType.entries.filter {
        it == mapping.gesture || it !in usedGestures
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gesture dropdown
        ExposedDropdownMenuBox(
            expanded = gestureExpanded,
            onExpandedChange = { gestureExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = mapping.gesture.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gestureExpanded) },
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true
            )

            ExposedDropdownMenu(
                expanded = gestureExpanded,
                onDismissRequest = { gestureExpanded = false }
            ) {
                availableGestures.forEach { gesture ->
                    DropdownMenuItem(
                        text = { Text(gesture.displayName, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            onGestureChange(gesture)
                            gestureExpanded = false
                        }
                    )
                }
            }
        }

        // Action dropdown
        ExposedDropdownMenuBox(
            expanded = actionExpanded,
            onExpandedChange = { actionExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = mapping.action.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionExpanded) },
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true
            )

            ExposedDropdownMenu(
                expanded = actionExpanded,
                onDismissRequest = { actionExpanded = false }
            ) {
                GestureAction.entries.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.displayName, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            onActionChange(action)
                            actionExpanded = false
                        }
                    )
                }
            }
        }

        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove gesture",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
