package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

@Composable
fun PenToolbar() {
    val currentProfile by EditorState.currentPenProfile.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var wasExpandedOnPress by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                wasExpandedOnPress = expanded
            }
        }
    }

    LaunchedEffect(Unit) {
        EditorState.dismissSettings.collect {
            Log.d(TAG, "dismissSettings received — closing dropdown settingg expanded false")
            expanded = false
        }
    }

    Box {
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
            OutlinedButton(
                onClick = {
                    Log.d(TAG, "Pen settings button clicked expanded=${expanded} wasExpandedOnPress=${wasExpandedOnPress}")
                    if (wasExpandedOnPress) {
                        // Menu was open when finger went down — onDismiss already closed it, don't reopen
                        Log.d(TAG, "Dropdown was open on press, onDismiss handled close")
                        EditorState.setMode(AppMode.DRAWING)
                    } else {
                        expanded = true
                        Log.d(TAG, "Opening dropdown")
                        EditorState.setMode(AppMode.SETTINGS)
                    }
                },
                interactionSource = interactionSource
            ) {
                Text("Pen Settings")
            }
        }

        PenPropertiesDropdown(
            expanded = expanded,
            currentProfile = currentProfile,
            onProfileChanged = { newProfile ->
                EditorState.setPenProfile(newProfile)
            },
            onDismiss = {
                Log.d(TAG, "PenPropertiesDropdown onDismiss setting expanded false")
                expanded = false
            }
        )
    }
}
