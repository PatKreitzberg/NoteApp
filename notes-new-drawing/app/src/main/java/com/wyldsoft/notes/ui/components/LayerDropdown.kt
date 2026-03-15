package com.wyldsoft.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel

@Composable
fun LayerDropdown(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var moveFromLayer by remember { mutableStateOf(1) }

    val activeLayer by viewModel.activeLayer.collectAsState()
    val hiddenLayers by viewModel.hiddenLayers.collectAsState()
    val soloLayer by viewModel.soloLayer.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.closeAllDropdownsEvent.collect { expanded = false }
    }

    Box(modifier = modifier) {
        IconButton(
            onClick = {
                if (expanded) {
                    expanded = false
                    viewModel.onDropdownClosed()
                } else {
                    expanded = true
                    viewModel.onDropdownOpened()
                }
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Layers",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "$activeLayer",
                    fontSize = 10.sp,
                    color = Color.Black
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                viewModel.onDropdownClosed()
            }
        ) {
            val existingLayers = viewModel.getExistingLayers()
            // Always show at least the active layer
            val allLayers = (existingLayers + activeLayer).distinct().sorted()

            for (layer in allLayers) {
                val isActive = layer == activeLayer
                val isHidden = layer in hiddenLayers
                val isSolo = layer == soloLayer

                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Layer $layer",
                                fontSize = 14.sp,
                                color = if (isActive) Color.Black else Color.Gray,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (isActive) Modifier
                                            .background(Color.LightGray, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp)
                                        else Modifier
                                    )
                            )

                            // Visibility toggle
                            Icon(
                                imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isHidden) "Show layer" else "Hide layer",
                                tint = if (isHidden) Color.LightGray else Color.DarkGray,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { viewModel.toggleLayerVisibility(layer) }
                            )

                            // Solo toggle
                            Icon(
                                imageVector = Icons.Default.FilterAlt,
                                contentDescription = "Solo layer",
                                tint = if (isSolo) Color.Black else Color.LightGray,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { viewModel.setSoloLayer(layer) }
                            )
                        }
                    },
                    onClick = {
                        viewModel.setActiveLayer(layer)
                    }
                )
            }

            // Move layer strokes item (only show if multiple layers exist)
            if (allLayers.size > 1) {
                DropdownMenuItem(
                    text = { Text("Move strokes...", fontSize = 14.sp) },
                    onClick = {
                        moveFromLayer = activeLayer
                        showMoveDialog = true
                    }
                )
            }

            // Add new layer
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add layer",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Add layer", fontSize = 14.sp)
                    }
                },
                onClick = {
                    viewModel.addLayer()
                    expanded = false
                    viewModel.onDropdownClosed()
                }
            )
        }
    }

    if (showMoveDialog) {
        MoveLayerDialog(
            fromLayer = moveFromLayer,
            existingLayers = viewModel.getExistingLayers(),
            onMove = { toLayer ->
                viewModel.moveLayerStrokes(moveFromLayer, toLayer)
                showMoveDialog = false
            },
            onDismiss = { showMoveDialog = false }
        )
    }
}

@Composable
private fun MoveLayerDialog(
    fromLayer: Int,
    existingLayers: List<Int>,
    onMove: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val targetLayers = existingLayers.filter { it != fromLayer }
    if (targetLayers.isEmpty()) {
        onDismiss()
        return
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move strokes from Layer $fromLayer") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("Move all strokes to:", fontSize = 14.sp)
                for (layer in targetLayers) {
                    Text(
                        text = "Layer $layer",
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clickable { onMove(layer) }
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
