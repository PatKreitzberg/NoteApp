package com.wyldsoft.notes.editor

import android.graphics.Rect
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import android.util.Log

/**
 * Global event bus for drawing lifecycle and UI state, using SharedFlows.
 *
 * Emits events that coordinate drawing activity and UI:
 * - drawingStarted / drawingEnded: fired by OnyxDrawingActivity's RawInputCallback
 *   so the UI can hide overlays during active drawing.
 * - forceScreenRefresh: triggers a full bitmap re-render to the SurfaceView.
 * - refreshUi: triggers Compose recomposition for UI elements.
 *
 * Holds a reference to BaseDrawingActivity for screen-refresh operations.
 * All emissions use GlobalScope to fire-and-forget from non-coroutine contexts.
 */
class EditorState {
    companion object {
        val refreshUi = MutableSharedFlow<Unit>()
        val drawingStarted = MutableSharedFlow<Unit>()
        val drawingEnded = MutableSharedFlow<Unit>()
        val forceScreenRefresh = MutableSharedFlow<Unit>()

        private var mainActivity: BaseDrawingActivity? = null

        fun setMainActivity(activity: BaseDrawingActivity) {
            mainActivity = activity
        }

        fun notifyDrawingStarted() {
            kotlinx.coroutines.GlobalScope.launch {
                drawingStarted.emit(Unit)
            }
        }

        fun notifyDrawingEnded() {
            kotlinx.coroutines.GlobalScope.launch {
                drawingEnded.emit(Unit)
                forceScreenRefresh.emit(Unit)
            }
        }

        fun getCurrentExclusionRects(): List<Rect> {
            return mainActivity?.let { activity ->
                emptyList<Rect>()
            } ?: emptyList()
        }

        fun forceRefresh() {
            Log.d("EditorState:", "forceRefresh()")
            kotlinx.coroutines.GlobalScope.launch {
                forceScreenRefresh.emit(Unit)
            }
            mainActivity?.let { activity ->
                activity.runOnUiThread {
                    kotlinx.coroutines.GlobalScope.launch {
                        refreshUi.emit(Unit)
                    }
                }
            }
        }
    }
}