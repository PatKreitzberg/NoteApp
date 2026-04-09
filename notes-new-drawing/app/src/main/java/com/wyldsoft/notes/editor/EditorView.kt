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
import com.wyldsoft.notes.ui.toolbar.TextPropertiesPanel
import com.wyldsoft.notes.ui.toolbar.Toolbar

/**
 * Top-level Compose layout for the editor screen.
 * Hosts the Toolbar, DrawingCanvas, and optional settings panels
 * with a fullscreen scrim for dismissal.
 */
@Composable
fun EditorView(
    onSurfaceViewCreated: (android.view.SurfaceView) -> Unit = {},
    gestureLabel: MutableState<String> = remember { mutableStateOf("") },
    resetViewport: () -> Unit = {}
) {
    remember { EditorState() }

    var menuExpanded by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var textExpanded by remember { mutableStateOf(false) }
    val currentProfile by EditorState.currentPenProfile.collectAsState()
    val textProfile by EditorState.textProfile.collectAsState()
    val currentMode by EditorState.currentMode.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Toolbar(
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it },
                settingsExpanded = settingsExpanded,
                onSettingsExpandedChange = { settingsExpanded = it },
                textExpanded = textExpanded,
                onTextExpandedChange = { textExpanded = it },
                resetViewport = { resetViewport() }
            )

            DrawingCanvas(
                onSurfaceViewCreated = onSurfaceViewCreated,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        // Scrim + panels overlay when any panel is open
        if (menuExpanded || settingsExpanded) {
            // Fullscreen transparent scrim — catches finger taps outside the panel
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        menuExpanded = false
                        settingsExpanded = false
                        EditorState.setMode(AppMode.DRAWING)
                    }
            )

            if (menuExpanded) {
                // Pen settings panel positioned below the toolbar
                PenPropertiesPanel(
                    currentProfile = currentProfile,
                    onProfileChanged = { newProfile ->
                        EditorState.setPenProfile(newProfile)
                    },
                    modifier = Modifier.padding(top = 48.dp)
                )
            }

            if (settingsExpanded) {
                // Editor settings panel positioned below the toolbar, right-aligned
                EditorSettingsPanel(
                    modifier = Modifier
                        .padding(top = 48.dp)
                        .wrapContentWidth()
                        .align(androidx.compose.ui.Alignment.TopEnd)
                )
            }
        }

        // Text properties panel overlay (separate from SETTINGS — stays in TEXT mode)
        if (textExpanded && currentMode == AppMode.TEXT) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        textExpanded = false
                    }
            )
            TextPropertiesPanel(
                textProfile = textProfile,
                onProfileChanged = { EditorState.setTextProfile(it) },
                modifier = Modifier.padding(top = 48.dp)
            )
        }

        // Gesture notification overlay
        GestureDisplay(gestureLabel = gestureLabel)
    }
}
