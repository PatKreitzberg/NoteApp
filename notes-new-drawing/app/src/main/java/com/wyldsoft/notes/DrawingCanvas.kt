package com.wyldsoft.notes

import android.util.Log
import android.view.SurfaceView
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel

@Composable
fun DrawingCanvas(
    viewModel: EditorViewModel,
    onSurfaceViewCreated: (SurfaceView, EditorViewModel) -> Unit
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (width > 0 && height > 0) {
                            Log.d(
                                "DrawingCanvas",
                                "SurfaceView ready: ${width}x${height}"
                            )
                            onSurfaceViewCreated(this@apply, viewModel)
                            // Remove listener so it only fires once
                            viewTreeObserver.removeOnGlobalLayoutListener(this)
                        }
                    }
                }
                viewTreeObserver.addOnGlobalLayoutListener(listener)
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = {
            // Trigger recomposition when refreshTrigger changes
            // even if we don't need to change the SurfaceView itself
        }
    )
}



