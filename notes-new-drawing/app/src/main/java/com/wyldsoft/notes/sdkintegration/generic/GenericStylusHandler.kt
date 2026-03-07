package com.wyldsoft.notes.sdkintegration.generic

import android.graphics.PointF
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.Tool
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.shapemanagement.TransformMode
import com.wyldsoft.notes.sdkintegration.AbstractStylusHandler

/**
 * Stylus handler for non-Onyx devices using standard Android MotionEvent.
 * Accumulates touch points from MotionEvents and delegates shared logic
 * to AbstractStylusHandler.
 */
class GenericStylusHandler(
    surfaceView: SurfaceView,
    viewModel: EditorViewModel,
    bitmapManager: BitmapManager,
    shapesManager: ShapesManager,
    onDrawingStateChanged: (isDrawing: Boolean) -> Unit,
    onShapeCompleted: (id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long>) -> Unit,
    onShapeRemoved: (shapeId: String) -> Unit,
    onForceScreenRefresh: () -> Unit
) : AbstractStylusHandler(
    surfaceView, viewModel, bitmapManager, shapesManager,
    onDrawingStateChanged, onShapeCompleted, onShapeRemoved,
    onForceScreenRefresh
) {
    companion object {
        private const val TAG = "GenericStylusHandler"
    }

    // Accumulate points during a stroke
    private val currentPoints = mutableListOf<TouchPoint>()
    // Track how many points have been rendered incrementally
    private var lastRenderedPointIndex = 0

    /**
     * Process a MotionEvent from the SurfaceView's touch listener.
     * Returns true if the event was consumed (stylus/eraser).
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val toolType = event.getToolType(0)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS
        val isEraser = toolType == MotionEvent.TOOL_TYPE_ERASER
        val isFinger = toolType == MotionEvent.TOOL_TYPE_FINGER

        if (!isStylus && !isEraser && !isFinger) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isEraser) handleEraseBegin()
                else handleDrawBegin(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isEraser || isErasingInProgress) handleEraseMove(event)
                else handleDrawMove(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isEraser || isErasingInProgress) handleEraseEnd()
                else handleDrawEnd()
            }
        }
        return true
    }

    // --- Drawing ---

    private fun handleDrawBegin(event: MotionEvent) {
        val tool = viewModel.uiState.value.selectedTool
        if (tool == Tool.SELECTOR) {
            val tp = motionEventToTouchPoint(event, 0)
            beginSelectionStroke(tp)
            return
        }
        if (tool == Tool.GEOMETRY) {
            val tp = motionEventToTouchPoint(event, 0)
            beginGeometryDrawing(tp)
            currentPoints.clear()
            currentPoints.add(tp)
            return
        }
        beginDrawing()
        currentPoints.clear()
        lastRenderedPointIndex = 0
        addPointsFromEvent(event)
    }

    private fun handleDrawMove(event: MotionEvent) {
        val tool = viewModel.uiState.value.selectedTool
        if (tool == Tool.GEOMETRY) {
            val tp = motionEventToTouchPoint(event, 0)
            currentPoints.add(tp)
            updateGeometryPreview(tp)
            return
        }
        if (tool == Tool.SELECTOR && selectionManager.hasSelection) {
            val isTransforming = selectionManager.isDragging ||
                selectionManager.transformMode == TransformMode.SCALE ||
                selectionManager.transformMode == TransformMode.ROTATE

            if (!isTransforming) {
                refreshCount++
                if (refreshCount < REFRESH_COUNT_LIMIT) return
                refreshCount = 0
            }

            val tp = motionEventToTouchPoint(event, event.pointerCount - 1)
            handleSelectionMoveUpdate(tp)
            return
        }
        addPointsFromEvent(event)
        drawIncrementalSegments()
    }

    private fun handleDrawEnd() {
        val tool = viewModel.uiState.value.selectedTool
        if (tool == Tool.GEOMETRY) {
            val touchPointList = buildTouchPointList()
            finalizeGeometryShape(touchPointList)
            currentPoints.clear()
            return
        }
        if (handleCancelledStroke()) {
            currentPoints.clear()
            return
        }
        val touchPointList = buildTouchPointList()
        if (handleSelectorStrokeEnd(touchPointList)) {
            currentPoints.clear()
            return
        }
        if (currentPoints.isNotEmpty()) {
            finalizeStroke(touchPointList)
        } else {
            isDrawingInProgress = false
            onDrawingStateChanged(false)
            viewModel.endDrawing()
        }
        currentPoints.clear()
    }

    // --- Erasing ---

    private fun handleEraseBegin() {
        beginErasing()
        currentPoints.clear()
        Log.d(TAG, "Erasing started")
    }

    private fun handleEraseMove(event: MotionEvent) {
        addPointsFromEvent(event)
    }

    private fun handleEraseEnd() {
        if (currentPoints.isNotEmpty()) {
            val touchPointList = buildTouchPointList()
            val noteErasePointList = convertTouchPointListToNoteCoordinates(touchPointList)
            finalizeErase(noteErasePointList)
        }
        endErasing()
        currentPoints.clear()
        Log.d(TAG, "Erasing ended")
    }

    // --- Incremental drawing ---

    private fun drawIncrementalSegments() {
        val startIdx = if (lastRenderedPointIndex > 0) lastRenderedPointIndex - 1 else 0
        if (currentPoints.size < 2 || startIdx >= currentPoints.size - 1) return
        bitmapManager.drawSegmentsToScreen(currentPoints, startIdx, currentPenProfile)
        lastRenderedPointIndex = currentPoints.size
    }

    // --- Helpers ---

    private fun buildTouchPointList(): TouchPointList {
        val touchPointList = TouchPointList()
        currentPoints.forEach { touchPointList.add(it) }
        return touchPointList
    }

    private fun addPointsFromEvent(event: MotionEvent) {
        for (h in 0 until event.historySize) {
            val tp = TouchPoint(
                event.getHistoricalX(h),
                event.getHistoricalY(h),
                event.getHistoricalPressure(h),
                event.getHistoricalSize(h),
                event.getHistoricalEventTime(h)
            )
            currentPoints.add(tp)
        }
        val tp = motionEventToTouchPoint(event, 0)
        currentPoints.add(tp)
    }

    private fun motionEventToTouchPoint(event: MotionEvent, pointerIndex: Int): TouchPoint {
        return TouchPoint(
            event.getX(pointerIndex),
            event.getY(pointerIndex),
            event.getPressure(pointerIndex),
            event.getSize(pointerIndex),
            event.eventTime
        )
    }
}
