package com.wyldsoft.notes.editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.DrawingCanvas
import com.wyldsoft.notes.touchhandling.GestureDisplay

/**
 * Top-level Compose layout for the editor screen.
 * Creates an EditorState instance and hosts the DrawingCanvas composable.
 * The [onSurfaceViewCreated] callback lets BaseDrawingActivity attach
 * SDK touch handling to the SurfaceView after it is inflated.
 * The [gestureLabel] state is updated by the GestureHandler and displayed
 * as an auto-dismissing overlay via GestureDisplay.
 */
@Composable
fun EditorView(
    onSurfaceViewCreated: (android.view.SurfaceView) -> Unit = {},
    gestureLabel: MutableState<String> = remember { mutableStateOf("") }
) {
    remember { EditorState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(16.dp))

            // Drawing canvas with real Onyx SDK integration
            DrawingCanvas(
                onSurfaceViewCreated = onSurfaceViewCreated
            )
        }

        // Gesture notification overlay
        GestureDisplay(gestureLabel = gestureLabel)
    }
}