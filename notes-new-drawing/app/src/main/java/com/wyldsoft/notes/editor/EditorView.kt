package com.wyldsoft.notes.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.DrawingCanvas
import com.wyldsoft.notes.touchhandling.GestureDisplay
import com.wyldsoft.notes.ui.toolbar.PenPropertiesPanel
import com.wyldsoft.notes.ui.toolbar.PenToolbar

/**
 * Top-level Compose layout for the editor screen.
 * Hosts the PenToolbar, DrawingCanvas, and an optional settings panel
 * with a fullscreen scrim for dismissal.
 */
@Composable
fun EditorView(
    onSurfaceViewCreated: (android.view.SurfaceView) -> Unit = {},
    gestureLabel: MutableState<String> = remember { mutableStateOf("") }
) {
    remember { EditorState() }

    var menuExpanded by remember { mutableStateOf(false) }
    val currentProfile by EditorState.currentPenProfile.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PenToolbar(
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it }
            )

            DrawingCanvas(
                onSurfaceViewCreated = onSurfaceViewCreated,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        // Scrim + settings panel overlay when menu is open
        if (menuExpanded) {
            // Fullscreen transparent scrim — catches finger taps outside the panel
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        menuExpanded = false
                        EditorState.setMode(AppMode.DRAWING)
                    }
            )

            // Settings panel positioned below the toolbar
            PenPropertiesPanel(
                currentProfile = currentProfile,
                onProfileChanged = { newProfile ->
                    EditorState.setPenProfile(newProfile)
                },
                modifier = Modifier.padding(top = 48.dp)
            )
        }

        // Gesture notification overlay
        GestureDisplay(gestureLabel = gestureLabel)
    }
}
