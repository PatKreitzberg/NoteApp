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
import com.wyldsoft.notes.settings.DisplaySettingsRepository
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    gestureSettingsRepository: GestureSettingsRepository,
    displaySettingsRepository: DisplaySettingsRepository,
    onDismiss: () -> Unit,
    onOpenGoogleDrive: () -> Unit = {}
) {
    val savedMappings by gestureSettingsRepository.mappings.collectAsState()
    var mappings by remember { mutableStateOf(savedMappings) }

    val currentRefreshRate by displaySettingsRepository.maxRefreshRate.collectAsState()
    val currentSmoothMotion by displaySettingsRepository.smoothMotion.collectAsState()
    val currentScrollBarVisible by displaySettingsRepository.scrollBarVisible.collectAsState()
    var refreshRate by remember { mutableStateOf(currentRefreshRate.toFloat()) }
    var smoothMotion by remember { mutableStateOf(currentSmoothMotion) }
    var scrollBarVisible by remember { mutableStateOf(currentScrollBarVisible) }

    val saveAndDismiss = {
        gestureSettingsRepository.saveMappings(mappings)
        displaySettingsRepository.setMaxRefreshRate(refreshRate.roundToInt())
        displaySettingsRepository.setSmoothMotion(smoothMotion)
        displaySettingsRepository.setScrollBarVisible(scrollBarVisible)
        onDismiss()
    }

    SettingsDialogShell(
        title = "App Settings",
        onDismiss = saveAndDismiss,
        scrollable = true
    ) {
        Text(
            text = "Display",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Smooth motion", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = smoothMotion,
                onCheckedChange = { smoothMotion = it }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Show scroll bar", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = scrollBarVisible,
                onCheckedChange = { scrollBarVisible = it }
            )
        }

        Text(
            text = "Max refresh rate: ${refreshRate.roundToInt()} Hz",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = refreshRate,
            onValueChange = { refreshRate = it },
            valueRange = 1f..15f,
            steps = 13,
            modifier = Modifier.fillMaxWidth()
        )

        Divider()

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

        Divider()

        Text(
            text = "Sync",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedButton(
            onClick = {
                gestureSettingsRepository.saveMappings(mappings)
                onOpenGoogleDrive()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Google Drive")
        }
    }
}

@Composable
private fun GestureMappingRow(
    mapping: GestureMapping,
    usedGestures: Set<GestureType>,
    onGestureChange: (GestureType) -> Unit,
    onActionChange: (GestureAction) -> Unit,
    onRemove: () -> Unit
) {
    val availableGestures = GestureType.entries.filter {
        it == mapping.gesture || it !in usedGestures
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InlineDropdown(
            items = availableGestures,
            selectedItem = mapping.gesture,
            displayName = { it.displayName },
            onItemSelected = onGestureChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall
        )

        InlineDropdown(
            items = GestureAction.entries,
            selectedItem = mapping.action,
            displayName = { it.displayName },
            onItemSelected = onActionChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall
        )

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
