package com.wyldsoft.notes.editor

import android.annotation.SuppressLint
import android.graphics.Rect
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        private const val TAG = "EditorState"
        private val _currentMode = MutableStateFlow(AppMode.DRAWING)
        val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

        @SuppressLint("StaticFieldLeak")
        private var mainActivity: BaseDrawingActivity? = null

        fun setMainActivity(activity: BaseDrawingActivity) {
            mainActivity = activity
        }

        fun setMode(mode: AppMode) {
            val previousMode = _currentMode.value
            if (previousMode == mode) return
            Log.d(TAG, "setMode: $previousMode -> $mode")
            _currentMode.value = mode
        }

        fun getCurrentExclusionRects(): List<Rect> {
            return mainActivity?.let { activity ->
                emptyList<Rect>()
            } ?: emptyList()
        }
    }
}