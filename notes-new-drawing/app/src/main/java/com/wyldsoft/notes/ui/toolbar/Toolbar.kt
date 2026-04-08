package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.editor.AppMode
import com.wyldsoft.notes.editor.EditorState
import com.wyldsoft.notes.R.drawable

private const val TAG = "Toolbar"

/**
 * Toolbar row with pen slot buttons, selection mode button, undo/redo, and editor settings.
 * The [expanded] state and [onExpandedChange] callback are hoisted to EditorView.
 * The [settingsExpanded] and [onSettingsExpandedChange] callbacks are for the editor settings panel.
 */
@Composable
fun Toolbar(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    settingsExpanded: Boolean,
    onSettingsExpandedChange: (Boolean) -> Unit,
    resetViewport: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        EditorState.dismissSettings.collect {
            Log.d(TAG, "dismissSettings received — closing panels")
            onExpandedChange(false)
            onSettingsExpandedChange(false)
            EditorState.setMode(AppMode.DRAWING)
        }
    }

    val penProfiles = listOf(
        EditorState.penProfile1.collectAsState().value,
        EditorState.penProfile2.collectAsState().value,
        EditorState.penProfile3.collectAsState().value,
        EditorState.penProfile4.collectAsState().value,
        EditorState.penProfile5.collectAsState().value,
    )
    val activePenSlot by EditorState.activePenSlot.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, Color.Black)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        penProfiles.forEachIndexed { index, profile ->
            val slot = index + 1
            PenSlotButton(
                slot = slot,
                penProfile = profile,
                isActive = activePenSlot == slot,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                onSettingsExpandedChange = onSettingsExpandedChange
            )
            if (slot < penProfiles.size) {
                Spacer(modifier = Modifier.width(6.dp))
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        val currentMode by EditorState.currentMode.collectAsState()
        val inSelection = currentMode == AppMode.SELECTION
        val inSeparation = currentMode == AppMode.SEPARATION
        val hasSelection by EditorState.hasSelection.collectAsState()
        val hasCopied by EditorState.hasCopied.collectAsState()
        val paginationEnabled by EditorState.paginationEnabled.collectAsState()

        IconButton(
            onClick = {
                Log.d(TAG, "Selection button clicked, inSelection=$inSelection")
                if (inSelection) EditorState.setMode(AppMode.DRAWING)
                else EditorState.setMode(AppMode.SELECTION)
            },
            modifier = Modifier.then(if (inSelection) Modifier.border(2.dp, Color.Black) else Modifier)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = drawable.lasso_select),
                contentDescription = "Selection Tool",
                tint = if (inSelection) Color.Black else Color.Gray
            )
        }

        IconButton(
            onClick = {
                Log.d(TAG, "Separation button clicked, inSeparation=$inSeparation")
                if (inSeparation) EditorState.setMode(AppMode.DRAWING)
                else EditorState.setMode(AppMode.SEPARATION)
            },
            enabled = paginationEnabled,
            modifier = Modifier.then(if (inSeparation) Modifier.border(2.dp, Color.Black) else Modifier)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = drawable.ic_separation),
                contentDescription = "Separation Mode",
                tint = when {
                    inSeparation -> Color.Black
                    paginationEnabled -> Color.Gray
                    else -> Color.LightGray
                }
            )
        }

        if (hasSelection) {
            IconButton(
                onClick = {
                    Log.d(TAG, "Copy button clicked")
                    EditorState.requestCopy()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (hasCopied) {
            IconButton(
                onClick = {
                    Log.d(TAG, "Paste button clicked")
                    EditorState.requestPaste()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Paste",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        IconButton(
            onClick = {
                Log.d(TAG, "Reset viewport button clicked")
                resetViewport()
            }
        ) {
            Icon(
                imageVector = Icons.Default.CenterFocusStrong,
                contentDescription = "ResetViewport"
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        val notesInNotebook by EditorState.notesInNotebook.collectAsState()
        val currentNoteIndex by EditorState.currentNoteIndex.collectAsState()

        if (notesInNotebook.isNotEmpty()) {
            IconButton(
                onClick = {
                    Log.d(TAG, "Navigate prev note")
                    EditorState.requestNavigatePrev()
                },
                enabled = currentNoteIndex > 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Previous note",
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "${currentNoteIndex + 1} / ${notesInNotebook.size}",
                fontSize = 13.sp
            )
            IconButton(
                onClick = {
                    Log.d(TAG, "Navigate next note")
                    EditorState.requestNavigateNext()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Next note",
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        val canUndo by EditorState.canUndo.collectAsState()
        val canRedo by EditorState.canRedo.collectAsState()

        IconButton(
            onClick = {
                Log.d(TAG, "Undo button clicked")
                EditorState.requestUndo()
            },
            enabled = canUndo
        ) {
            Icon(
                imageVector = Icons.Default.Undo,
                contentDescription = "Undo",
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(
            onClick = {
                Log.d(TAG, "Redo button clicked")
                EditorState.requestRedo()
            },
            enabled = canRedo
        ) {
            Icon(
                imageVector = Icons.Default.Redo,
                contentDescription = "Redo",
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = {
            Log.d(TAG, "Settings button clicked, settingsExpanded=$settingsExpanded")
            if (!settingsExpanded) {
                onSettingsExpandedChange(true)
                onExpandedChange(false)
                EditorState.setMode(AppMode.SETTINGS)
            } else {
                onSettingsExpandedChange(false)
                EditorState.setMode(AppMode.DRAWING)
            }
        }) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Editor settings",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
