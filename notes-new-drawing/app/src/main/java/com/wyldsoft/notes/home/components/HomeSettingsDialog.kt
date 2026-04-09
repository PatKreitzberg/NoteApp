package com.wyldsoft.notes.home.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wyldsoft.notes.gestures.GestureAction
import com.wyldsoft.notes.gestures.GestureBindings

@Composable
fun HomeSettingsDialog(
    defaultPaginationEnabled: Boolean,
    onDefaultPaginationChanged: (Boolean) -> Unit,
    gestureMappings: Map<String, GestureAction>,
    onGestureMappingsChanged: (Map<String, GestureAction>) -> Unit,
    scribbleToEraseEnabled: Boolean,
    onScribbleToEraseToggle: (Boolean) -> Unit,
    circleToSelectEnabled: Boolean,
    onCircleToSelectToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var pagination by remember { mutableStateOf(defaultPaginationEnabled) }
    var mappings by remember(gestureMappings) { mutableStateOf(gestureMappings) }
    var scribbleToErase by remember { mutableStateOf(scribbleToEraseEnabled) }
    var circleToSelect by remember { mutableStateOf(circleToSelectEnabled) }

    // Swallows any scroll that the inner gesture list doesn't consume,
    // preventing it from propagating to any outer scroll container.
    val scrollBarrier = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = available
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Black, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Title
                Text(
                    text = "Default Settings",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "These settings apply to newly created notebooks.",
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Pagination toggle
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

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Ink Gestures",
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scribble to erase",
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = scribbleToErase,
                        onCheckedChange = { scribbleToErase = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Circle to select",
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = circleToSelect,
                        onCheckedChange = { circleToSelect = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color.Black)
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

                // Column header
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

                // Scrollable gesture list — fixed height, outlined, scroll-isolated
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .border(1.dp, Color.Black)
                        .nestedScroll(scrollBarrier)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 2.dp)
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

                // Buttons
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        onDefaultPaginationChanged(pagination)
                        onGestureMappingsChanged(mappings)
                        onScribbleToEraseToggle(scribbleToErase)
                        onCircleToSelectToggle(circleToSelect)
                        onDismiss()
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
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
