package com.wyldsoft.notes.editor

import android.annotation.SuppressLint
import android.graphics.Rect
import android.util.Log
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

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
        var previousMode = AppMode.DRAWING
        val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

        private val _dismissSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val dismissSettings = _dismissSettings.asSharedFlow()

        private val _currentPenProfile = MutableStateFlow(PenProfile.getDefaultProfile(PenType.BALLPEN))
        val currentPenProfile: StateFlow<PenProfile> = _currentPenProfile.asStateFlow()

        private var toolbarRect: Rect? = null
        var exclusionRects = mutableListOf<Rect>()

        @SuppressLint("StaticFieldLeak")
        private var mainActivity: BaseDrawingActivity? = null

        fun setMainActivity(activity: BaseDrawingActivity) {
            mainActivity = activity
        }

        fun setMode(mode: AppMode) {
            Log.d(TAG, "setMode: ${_currentMode.value} -> $mode")

            previousMode = _currentMode.value
            if (previousMode == mode) return
            _currentMode.value = mode
        }

        fun emitDismissSettings() {
            Log.d(TAG, "emitDismissSettings")
            _dismissSettings.tryEmit(Unit)
        }

        fun setPenProfile(profile: PenProfile) {
            Log.d(TAG, "setPenProfile: ${profile.penType.displayName}, width=${profile.strokeWidth}")
            _currentPenProfile.value = profile
        }

        fun addExclusionRect(rect: Rect) {
            exclusionRects.add(rect)
        }

        fun getCurrentExclusionRects(): List<Rect> {
            return exclusionRects
        }
    }
}