package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
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
import com.wyldsoft.notes.editor.AppMode
import com.wyldsoft.notes.editor.EditorState

private const val TAG = "PenToolbar"

/**
 * Toolbar row with a button that opens the pen settings panel.
 * The [expanded] state and [onExpandedChange] callback are hoisted
 * to EditorView so the scrim can be managed at the same level.
 */
@Composable
fun PenToolbar(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val currentProfile by EditorState.currentPenProfile.collectAsState()

    LaunchedEffect(Unit) {
        EditorState.dismissSettings.collect {
            Log.d(TAG, "dismissSettings received — closing panel")
            onExpandedChange(false)
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
                EditorState.setMode(AppMode.SETTINGS)
            } else {
                onExpandedChange(false)
                EditorState.setMode(AppMode.DRAWING)
            }
        }) {
            Text("Pen Settings")
        }
    }
}
