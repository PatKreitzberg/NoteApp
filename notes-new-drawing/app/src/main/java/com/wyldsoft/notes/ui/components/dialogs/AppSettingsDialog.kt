package com.wyldsoft.notes.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.gestures.GestureAction
import com.wyldsoft.notes.gestures.GestureSettingsRepository
import com.wyldsoft.notes.gestures.GestureType
import com.wyldsoft.notes.gestures.GestureMapping
import com.wyldsoft.notes.settings.DisplaySettingsRepository
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    gestureSettingsRepository: GestureSettingsRepository,
    displaySettingsRepository: DisplaySettingsRepository,
    defaultNoteSettingsRepository: com.wyldsoft.notes.settings.DefaultNoteSettingsRepository? = null,
    onDismiss: () -> Unit,
    onOpenGoogleDrive: () -> Unit = {}
) {
    val savedMappings by gestureSettingsRepository.mappings.collectAsState()
    // Build a mutable map of gesture → action, defaulting unmapped gestures to NONE
    var gestureActions by remember(savedMappings) {
        mutableStateOf(
            GestureType.entries.associateWith { gesture ->
                savedMappings.find { it.gesture == gesture }?.action ?: GestureAction.NONE
            }
        )
    }

    val currentRefreshRate by displaySettingsRepository.maxRefreshRate.collectAsState()
    val currentSmoothMotion by displaySettingsRepository.smoothMotion.collectAsState()
    val currentScrollBarVisible by displaySettingsRepository.scrollBarVisible.collectAsState()
    var refreshRate by remember { mutableStateOf(currentRefreshRate.toFloat()) }
    var smoothMotion by remember { mutableStateOf(currentSmoothMotion) }
    var scrollBarVisible by remember { mutableStateOf(currentScrollBarVisible) }

    var showDefaultNoteSettings by remember { mutableStateOf(false) }

    val saveAndDismiss = {
        val mappings = gestureActions
            .filter { (_, action) -> action != GestureAction.NONE }
            .map { (gesture, action) -> GestureMapping(gesture, action) }
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

        if (defaultNoteSettingsRepository != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showDefaultNoteSettings = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Default Note Settings")
            }
        }

        Divider()

        Text(
            text = "Gestures",
            style = MaterialTheme.typography.titleMedium
        )

        GestureType.entries.forEach { gesture ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = gesture.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                InlineDropdown(
                    items = GestureAction.entries,
                    selectedItem = gestureActions[gesture] ?: GestureAction.NONE,
                    displayName = { it.displayName },
                    onItemSelected = { action ->
                        gestureActions = gestureActions.toMutableMap().apply { put(gesture, action) }
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        }

        Divider()

        Text(
            text = "Sync",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedButton(
            onClick = {
                val mappings = gestureActions
                    .filter { (_, action) -> action != GestureAction.NONE }
                    .map { (gesture, action) -> GestureMapping(gesture, action) }
                gestureSettingsRepository.saveMappings(mappings)
                onOpenGoogleDrive()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Google Drive")
        }
    }

    if (showDefaultNoteSettings && defaultNoteSettingsRepository != null) {
        DefaultNoteSettingsDialog(
            repository = defaultNoteSettingsRepository,
            onDismiss = { showDefaultNoteSettings = false }
        )
    }
}
