package com.wyldsoft.notes.sdkintegration

import android.util.Log
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.presentation.viewmodel.DrawTool
import com.wyldsoft.notes.presentation.viewmodel.EditorMode
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.shapemanagement.SelectionManager
import com.wyldsoft.notes.shapemanagement.shapes.TextShape

/**
 * Routes stylus input to the correct handler based on the current EditorMode.
 * Eliminates duplicated `when (mode)` dispatch across Onyx and Generic handlers.
 *
 * SDK-specific hooks (e.g. toggling raw drawing render) are provided via [Callbacks].
 *
 * The mode is captured at [routeBegin] time and reused in [routeEnd] to prevent
 * race conditions where async operations (e.g. circle-to-select, scribble-to-erase)
 * change the mode between begin and end of a stroke.
 */
class ModeInputRouter(
    private val viewModel: EditorViewModel,
    private val callbacks: Callbacks,
    private val selectionMoveThrottle: Int = 100
) {
    companion object {
        private const val TAG = "DROPSTROKEBUG"
    }

    interface Callbacks {
        fun onBeginPenOrEraser(touchPoint: TouchPoint?)
        fun onBeginGeometry(touchPoint: TouchPoint)
        fun onBeginSelection(touchPoint: TouchPoint?)
        fun onBeginText(touchPoint: TouchPoint)
        fun onMovePen(touchPoint: TouchPoint)
        fun onMoveGeometry(touchPoint: TouchPoint)
        fun onMoveSelection(touchPoint: TouchPoint)
        fun onEndPenOrEraser(touchPointList: TouchPointList)
        fun onEndGeometry(touchPointList: TouchPointList)
        fun onEndSelection(touchPointList: TouchPointList)
        /** Called when a cancelled selection stroke ends (stylus lifted after touching outside). */
        fun onEndCancelledStroke()
    }

    private val selectionManager: SelectionManager get() = viewModel.selectionManager
    private var selectionRefreshCount: Int = 0

    /** Mode captured at routeBegin time, used by routeEnd to avoid race conditions. */
    private var beginMode: EditorMode? = null

    fun routeBegin(touchPoint: TouchPoint?) {
        val mode = viewModel.uiState.value.mode
        beginMode = mode
        Log.d(TAG, "routeBegin: mode=$mode, touchPoint=${touchPoint != null}")
        when (mode) {
            is EditorMode.Select -> callbacks.onBeginSelection(touchPoint)
            is EditorMode.Text -> touchPoint?.let { callbacks.onBeginText(it) }
            is EditorMode.Draw -> when (mode.drawTool) {
                DrawTool.GEOMETRY -> touchPoint?.let { callbacks.onBeginGeometry(it) }
                DrawTool.PEN, DrawTool.ERASER -> callbacks.onBeginPenOrEraser(touchPoint)
            }
        }
    }

    fun routeMove(touchPoint: TouchPoint?) {
        if (touchPoint == null) return
        // Use captured beginMode for move routing to stay consistent with begin
        val mode = beginMode ?: viewModel.uiState.value.mode
        when (mode) {
            is EditorMode.Text -> return
            is EditorMode.Draw -> when (mode.drawTool) {
                DrawTool.GEOMETRY -> callbacks.onMoveGeometry(touchPoint)
                DrawTool.PEN -> callbacks.onMovePen(touchPoint)
                DrawTool.ERASER -> { /* eraser move handled separately */ }
            }
            is EditorMode.Select -> {
                if (!selectionManager.hasSelection) return
                if (selectionRefreshCount < selectionMoveThrottle) { selectionRefreshCount++; return }
                selectionRefreshCount = 0
                callbacks.onMoveSelection(touchPoint)
            }
        }
    }

    fun routeEnd(touchPointList: TouchPointList?) {
        if (touchPointList == null) {
            Log.w(TAG, "routeEnd: touchPointList is NULL — stroke dropped entirely!")
            beginMode = null
            return
        }

        val currentMode = viewModel.uiState.value.mode
        val capturedMode = beginMode
        beginMode = null

        if (capturedMode != null && capturedMode != currentMode) {
            Log.w(TAG, "routeEnd: MODE CHANGED during stroke! begin=$capturedMode, current=$currentMode — using captured mode")
        }

        // Use the mode captured at begin time to prevent race conditions
        val mode = capturedMode ?: currentMode
        Log.d(TAG, "routeEnd: mode=$mode, points=${touchPointList.size()}")

        when (mode) {
            is EditorMode.Text -> {
                Log.d(TAG, "routeEnd: Text mode — not finalizing stroke (expected)")
                return
            }
            is EditorMode.Draw -> when (mode.drawTool) {
                DrawTool.GEOMETRY -> callbacks.onEndGeometry(touchPointList)
                DrawTool.PEN, DrawTool.ERASER -> callbacks.onEndPenOrEraser(touchPointList)
            }
            is EditorMode.Select -> callbacks.onEndSelection(touchPointList)
        }
    }
}
