package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.editor.AppMode
import com.wyldsoft.notes.editor.EditorState

private const val TAG = "PenToolbar"

/**
 * Toolbar row with pen settings panel button, selection mode button, and editor settings button.
 * The [expanded] state and [onExpandedChange] callback are hoisted to EditorView.
 * The [settingsExpanded] and [onSettingsExpandedChange] callbacks are for the editor settings panel.
 */
@Composable
fun PenToolbar(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    settingsExpanded: Boolean,
    onSettingsExpandedChange: (Boolean) -> Unit
) {
    val currentProfile by EditorState.currentPenProfile.collectAsState()

    LaunchedEffect(Unit) {
        EditorState.dismissSettings.collect {
            Log.d(TAG, "dismissSettings received — closing panels")
            onExpandedChange(false)
            onSettingsExpandedChange(false)
            EditorState.setMode(AppMode.DRAWING)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, Color.Black)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${currentProfile.penType.displayName} · ${currentProfile.strokeWidth.toInt()}px",
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedButton(onClick = {
            Log.d(TAG, "Pen settings button clicked, expanded=$expanded")
            if (!expanded) {
                onExpandedChange(true)
                onSettingsExpandedChange(false)
                EditorState.setMode(AppMode.SETTINGS)
            } else {
                onExpandedChange(false)
                EditorState.setMode(AppMode.DRAWING)
            }
        }) {
            Text("Pen Settings")
        }

        Spacer(modifier = Modifier.width(12.dp))

        val currentMode by EditorState.currentMode.collectAsState()
        val inSelection = currentMode == AppMode.SELECTION
        OutlinedButton(
            onClick = {
                Log.d(TAG, "Selection button clicked, inSelection=$inSelection")
                if (inSelection) EditorState.setMode(AppMode.DRAWING)
                else EditorState.setMode(AppMode.SELECTION)
            },
            border = if (inSelection) BorderStroke(2.dp, Color.DarkGray) else null
        ) {
            Text("Select")
        }

        Spacer(modifier = Modifier.weight(1f))

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
