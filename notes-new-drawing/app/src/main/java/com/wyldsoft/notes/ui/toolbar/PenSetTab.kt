package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.AlertDialog
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.R.drawable
import com.wyldsoft.notes.ScrotesApp
import com.wyldsoft.notes.data.database.entities.PenProfileSetEntity
import com.wyldsoft.notes.editor.AppMode
import com.wyldsoft.notes.editor.EditorState
import com.wyldsoft.notes.pen.PenType
import kotlinx.coroutines.launch

private const val TAG = "PenSetTab"

/**
 * Layers icon button shown to the left of the five pen slot buttons.
 * Opens a dropdown to switch, manage (rename/update/delete), and create pen profile sets.
 * A small "•" dirty indicator overlays the icon when profiles have been modified since
 * the last set was loaded.
 */
@Composable
fun PenSetTab() {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as ScrotesApp).penProfileSetRepository }

    val sets by EditorState.penProfileSets.collectAsState()
    val activeSetId by EditorState.activeSetId.collectAsState()
    val isSetDirty by EditorState.isSetDirty.collectAsState()
    val activeSetName by EditorState.activeSetName.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Dialog state — close dropdown before showing any dialog
    var confirmDeleteTarget by remember { mutableStateOf<PenProfileSetEntity?>(null) }
    var confirmUpdateTarget by remember { mutableStateOf<PenProfileSetEntity?>(null) }
    var renameTarget by remember { mutableStateOf<PenProfileSetEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newSetName by remember { mutableStateOf("") }

    // ── Icon button with dirty dot overlay ─────────────────────────────────
    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
        Box {
            IconButton(
                onClick = {
                    Log.d(TAG, "PenSetTab clicked, sets=${sets.size}")
                    EditorState.setMode(AppMode.SETTINGS)
                    expanded = true
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Pen profile sets",
                    tint = Color.DarkGray,
                    modifier = Modifier.size(22.dp)
                )
            }
            // Dirty indicator dot
            if (isSetDirty && activeSetName.isNotBlank()) {
                Text(
                    text = "•",
                    fontSize = 14.sp,
                    color = Color.Red,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp, y = 2.dp)
                )
            }
        }

        // ── Dropdown ───────────────────────────────────────────────────────
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                renameTarget = null
                EditorState.setMode(AppMode.DRAWING)
            }
        ) {
            if (sets.isEmpty()) {
                DropdownMenuItem(onClick = {}) {
                    Text("No sets saved", fontSize = 13.sp, color = Color.Gray)
                }
            }

            sets.forEach { set ->
                val isRenaming = renameTarget?.id == set.id
                DropdownMenuItem(
                    onClick = {
                        if (!isRenaming) {
                            Log.d(TAG, "Load set id=${set.id}")
                            expanded = false
                            scope.launch { repository.loadSet(set.id) }
                            EditorState.setMode(AppMode.DRAWING)
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRenaming) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                singleLine = true,
                                modifier = Modifier.width(140.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                            )
                            IconButton(
                                onClick = {
                                    val trimmed = renameText.trim()
                                    if (trimmed.isNotBlank()) {
                                        scope.launch { repository.renameSet(set.id, trimmed) }
                                    }
                                    renameTarget = null
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Confirm rename",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { renameTarget = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel rename",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            // Set name
                            val isActive = set.id == activeSetId && !isSetDirty
                            Text(
                                text = if (isActive) "✓ ${set.name}" else set.name,
                                fontSize = 13.sp,
                                modifier = Modifier.width(120.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Mini toolbar preview (5 slots)
                            MiniSlotPreview(set = set)

                            Spacer(modifier = Modifier.weight(1f))

                            // Rename button
                            IconButton(
                                onClick = {
                                    renameTarget = set
                                    renameText = set.name
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename set",
                                    modifier = Modifier.size(15.dp),
                                    tint = Color.Gray
                                )
                            }

                            // Update/overwrite button
                            IconButton(
                                onClick = {
                                    expanded = false
                                    confirmUpdateTarget = set
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Overwrite set with current",
                                    modifier = Modifier.size(15.dp),
                                    tint = Color.Gray
                                )
                            }

                            // Delete button
                            IconButton(
                                onClick = {
                                    expanded = false
                                    confirmDeleteTarget = set
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete set",
                                    modifier = Modifier.size(15.dp),
                                    tint = Color.Gray
                                )
                            }
                        }
                    }
                }
                Divider()
            }

            // Create new set button
            DropdownMenuItem(
                onClick = {
                    expanded = false
                    newSetName = ""
                    showCreateDialog = true
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save current as new set", fontSize = 13.sp)
                }
            }
        }
    }

    // ── Confirmation: delete ────────────────────────────────────────────────
    confirmDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeleteTarget = null },
            title = { Text("Delete set?") },
            text = { Text("Delete \"${target.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteSet(target.id) }
                    confirmDeleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    // ── Confirmation: update/overwrite ──────────────────────────────────────
    confirmUpdateTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmUpdateTarget = null },
            title = { Text("Overwrite set?") },
            text = { Text("Replace \"${target.name}\" with the current pen slot profiles?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.updateCurrentIntoSet(target.id) }
                    confirmUpdateTarget = null
                }) { Text("Overwrite") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUpdateTarget = null }) { Text("Cancel") }
            }
        )
    }

    // ── Create new set dialog ───────────────────────────────────────────────
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New pen profile set") },
            text = {
                OutlinedTextField(
                    value = newSetName,
                    onValueChange = { newSetName = it },
                    placeholder = { Text("Set name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newSetName.trim()
                        if (trimmed.isNotBlank()) {
                            scope.launch { repository.saveCurrentAsNewSet(trimmed) }
                            showCreateDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Renders 5 tiny colored boxes with pen-type icons, mirroring the toolbar appearance
 * if this set were loaded. Each box is ~14×18dp.
 */
@Composable
private fun MiniSlotPreview(set: PenProfileSetEntity) {
    val slots = listOf(
        set.slot1ColorArgb to set.slot1PenType,
        set.slot2ColorArgb to set.slot2PenType,
        set.slot3ColorArgb to set.slot3PenType,
        set.slot4ColorArgb to set.slot4PenType,
        set.slot5ColorArgb to set.slot5PenType,
    )
    Row {
        slots.forEach { (colorArgb, penTypeName) ->
            val bgColor = Color(colorArgb)
            val penType = runCatching { PenType.valueOf(penTypeName) }.getOrDefault(PenType.BALLPEN)
            val iconRes = penTypeToDrawableRes(penType)
            val iconTint = contrastColor(bgColor)
            Box(
                modifier = Modifier
                    .size(width = 14.dp, height = 18.dp)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(10.dp)
                )
            }
            Spacer(modifier = Modifier.width(1.dp))
        }
    }
}

private fun penTypeToDrawableRes(penType: PenType): Int = when (penType) {
    PenType.BALLPEN -> drawable.ic_pen_hard
    PenType.FOUNTAIN -> drawable.ic_pen_fountain
    PenType.MARKER -> drawable.ic_marker_pen
    PenType.PENCIL -> drawable.ic_pencil
    PenType.CHARCOAL -> drawable.ic_charcoal
    PenType.CHARCOAL_V2 -> drawable.ic_charcoal_pen
    PenType.NEO_BRUSH -> drawable.ic_brush
    PenType.DASH -> drawable.ic_pen_soft
}

private fun contrastColor(bg: Color): Color {
    val luminance = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    return if (luminance > 0.5f) Color.Black else Color.White
}
