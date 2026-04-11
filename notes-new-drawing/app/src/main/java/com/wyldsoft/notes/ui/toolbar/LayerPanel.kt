package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.data.database.entities.LayerEntity

private const val TAG = "LayerPanel"

@Composable
fun LayerPanel(
    layers: List<LayerEntity>,
    activeLayer: Int,
    onSelectLayer: (Int) -> Unit,
    onAddLayer: () -> Unit,
    onDeleteLayer: (LayerEntity) -> Unit,
    onRenameLayer: (LayerEntity, String) -> Unit,
    onToggleVisibility: (LayerEntity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "LayerPanel layers=${layers.size} activeLayer=$activeLayer")

    var renameTarget by remember { mutableStateOf<LayerEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Layer") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Layer name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget
                    if (target != null && renameText.isNotBlank()) {
                        Log.d(TAG, "Renaming layer ${target.id} to $renameText")
                        onRenameLayer(target, renameText)
                    }
                    renameTarget = null
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* consume clicks */ },
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Layers", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // "All Layers" special row
                AllLayersRow(
                    isActive = activeLayer == -1,
                    onSelect = {
                        Log.d(TAG, "Selected All Layers")
                        onSelectLayer(-1)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // Render each real layer
                layers.forEach { layer ->
                    val isActive = activeLayer == layer.position
                    val isOnlyLayer = layers.size == 1
                    LayerRow(
                        layer = layer,
                        isActive = isActive,
                        canDelete = !isOnlyLayer,
                        onSelect = {
                            Log.d(TAG, "Selected layer position=${layer.position}")
                            onSelectLayer(layer.position)
                        },
                        onLongPress = {
                            Log.d(TAG, "Long press layer ${layer.id} for rename")
                            renameTarget = layer
                            renameText = layer.name
                        },
                        onDelete = {
                            Log.d(TAG, "Delete layer ${layer.id}")
                            onDeleteLayer(layer)
                        },
                        onToggleVisibility = {
                            Log.d(TAG, "Toggle visibility layer ${layer.id}")
                            onToggleVisibility(layer)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()

                // Add layer button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            Log.d(TAG, "Add layer clicked")
                            onAddLayer()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Layer"
                        )
                    }
                    Text("Add Layer", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun AllLayersRow(
    isActive: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActive) Modifier.background(Color(0xFFDDEEFF))
                else Modifier
            )
            .clickable { onSelect() }
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "All Layers",
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LayerRow(
    layer: LayerEntity,
    isActive: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onToggleVisibility: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActive) Modifier.background(Color(0xFFDDEEFF))
                else Modifier
            )
            .clickable { onSelect() }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Visibility toggle
        IconButton(
            onClick = onToggleVisibility,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (layer.visible) "Hide layer" else "Show layer",
                tint = if (layer.visible) Color.Black else Color.LightGray,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Layer name — long press triggers rename
        Text(
            text = layer.name,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            modifier = Modifier
                .weight(1f)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onSelect() }
        )

        // Rename hint text (long-press)
        Text(
            text = "✎",
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier
                .clickable { onLongPress() }
                .padding(horizontal = 4.dp)
        )

        // Delete button
        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete layer",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            // Spacer to keep layout consistent
            Spacer(modifier = Modifier.size(32.dp))
        }
    }
}
