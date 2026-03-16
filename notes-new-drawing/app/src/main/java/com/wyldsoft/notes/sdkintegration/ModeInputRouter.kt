package com.wyldsoft.notes.sdkintegration

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
 */
class ModeInputRouter(
    private val viewModel: EditorViewModel,
    private val callbacks: Callbacks,
    private val selectionMoveThrottle: Int = 100
) {
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

    fun routeBegin(touchPoint: TouchPoint?) {
        when (val mode = viewModel.uiState.value.mode) {
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
        when (val mode = viewModel.uiState.value.mode) {
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
        if (touchPointList == null) return
        when (val mode = viewModel.uiState.value.mode) {
            is EditorMode.Text -> return
            is EditorMode.Draw -> when (mode.drawTool) {
                DrawTool.GEOMETRY -> callbacks.onEndGeometry(touchPointList)
                DrawTool.PEN, DrawTool.ERASER -> callbacks.onEndPenOrEraser(touchPointList)
            }
            is EditorMode.Select -> callbacks.onEndSelection(touchPointList)
        }
    }
}
