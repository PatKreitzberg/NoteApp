package com.wyldsoft.notes.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.gestures.GestureAction
import com.wyldsoft.notes.gestures.GestureBindings

@Composable
fun HomeSettingsDialog(
    defaultPaginationEnabled: Boolean,
    onDefaultPaginationChanged: (Boolean) -> Unit,
    gestureMappings: Map<String, GestureAction>,
    onGestureMappingsChanged: (Map<String, GestureAction>) -> Unit,
    onDismiss: () -> Unit
) {
    var pagination by remember { mutableStateOf(defaultPaginationEnabled) }
    var mappings by remember(gestureMappings) { mutableStateOf(gestureMappings) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Settings") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "These settings apply to newly created notebooks.",
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pagination",
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = pagination,
                        onCheckedChange = { pagination = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Gesture Mappings",
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Assign actions to gestures in the editor.",
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Header row
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text(
                        text = "Gesture",
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Action",
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.width(160.dp)
                    )
                }

                // Scrollable gesture list capped at 300dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    GestureBindings.ALL_GESTURE_KEYS.forEach { (key, displayName) ->
                        GestureRow(
                            gestureName = displayName,
                            selectedAction = mappings[key] ?: GestureAction.NONE,
                            onActionSelected = { action ->
                                mappings = mappings + (key to action)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDefaultPaginationChanged(pagination)
                onGestureMappingsChanged(mappings)
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun GestureRow(
    gestureName: String,
    selectedAction: GestureAction,
    onActionSelected: (GestureAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = gestureName,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.width(160.dp).wrapContentSize(Alignment.TopStart)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedAction.displayName(),
                    style = MaterialTheme.typography.body2,
                    maxLines = 1
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                GestureAction.entries.forEach { action ->
                    DropdownMenuItem(onClick = {
                        onActionSelected(action)
                        expanded = false
                    }) {
                        Text(action.displayName())
                    }
                }
            }
        }
    }
}
