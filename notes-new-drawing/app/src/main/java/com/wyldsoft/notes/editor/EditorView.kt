package com.wyldsoft.notes.editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.DrawingCanvas

@Composable
fun EditorView(
    onSurfaceViewCreated: (android.view.SurfaceView) -> Unit = {}
) {
    val editorState = remember { EditorState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        // Drawing canvas with real Onyx SDK integration
        DrawingCanvas(
            onSurfaceViewCreated = onSurfaceViewCreated
        )
    }
}