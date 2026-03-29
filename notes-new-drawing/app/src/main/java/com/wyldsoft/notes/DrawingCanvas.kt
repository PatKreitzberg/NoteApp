package com.wyldsoft.notes

import android.view.SurfaceView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.wyldsoft.notes.editor.EditorState

/**
 * Composable that wraps an Android SurfaceView for drawing.
 * Acts as the bridge between Jetpack Compose and the native SurfaceView
 * that the Onyx SDK's TouchHelper draws into. The created SurfaceView
 * is passed back to the activity via [onSurfaceViewCreated] so that
 * BaseDrawingActivity can attach touch handling and rendering to it.
 */
@Composable
fun DrawingCanvas(
    onSurfaceViewCreated: (SurfaceView) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                // Surface will be configured by the activity
                onSurfaceViewCreated(this)
            }
        },
        modifier = modifier
    )
}
