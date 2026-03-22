package com.wyldsoft.notes.sdkintegration.generic

import android.graphics.PointF
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.sdkintegration.AbstractStylusHandler
import com.wyldsoft.notes.settings.DisplaySettingsRepository

/**
 * Stylus handler for non-Onyx devices using standard Android MotionEvent.
 * Accumulates touch points from MotionEvents and delegates mode-based
 * dispatch to [ModeInputRouter].
 */
class GenericStylusHandler(
    surfaceView: SurfaceView,
    viewModel: EditorViewModel,
    bitmapManager: BitmapManager,
    getShapesManager: () -> ShapesManager,
    displaySettingsRepository: DisplaySettingsRepository,
    onDrawingStateChanged: (isDrawing: Boolean) -> Unit,
    onShapeCompleted: (id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long>, tiltX: List<Int>, tiltY: List<Int>) -> Unit,
    onShapeRemoved: (shapeId: String) -> Unit,
    onForceScreenRefresh: () -> Unit
) : AbstractStylusHandler(
    surfaceView, viewModel, bitmapManager, getShapesManager, displaySettingsRepository,
    onDrawingStateChanged, onShapeCompleted, onShapeRemoved,
    onForceScreenRefresh
) {
    companion object {
        private const val TAG = "GenericStylusHandler"
    }

    private val modeRouter = createModeInputRouter()

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

    // --- Drawing (uses ModeInputRouter) ---

    private fun handleDrawBegin(event: MotionEvent) {
        if (viewModel.isAnyDropdownOpen) {
            viewModel.closeAllDropdowns()
            return
        }
        val tp = motionEventToTouchPoint(event, 0)
        currentPoints.clear()
        currentPoints.add(tp)
        lastRenderedPointIndex = 0
        modeRouter.routeBegin(tp)
    }

    private fun handleDrawMove(event: MotionEvent) {
        addPointsFromEvent(event)
        val tp = currentPoints.last()
        modeRouter.routeMove(tp)

        // Incremental segments only for pen drawing (not geometry/selection/text)
        if (isDrawingInProgress) {
            drawIncrementalSegments()
        }
    }

    private fun handleDrawEnd() {
        val touchPointList = buildTouchPointList()
        modeRouter.routeEnd(touchPointList)
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
            val tiltAngle = event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, h)
            val orientation = event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, h)
            val tx = (Math.sin(orientation.toDouble()) * Math.sin(tiltAngle.toDouble()) * 1000).toInt()
            val ty = (Math.cos(orientation.toDouble()) * Math.sin(tiltAngle.toDouble()) * 1000).toInt()
            val tp = TouchPoint(
                event.getHistoricalX(h),
                event.getHistoricalY(h),
                event.getHistoricalPressure(h),
                event.getHistoricalSize(h),
                tx, ty,
                event.getHistoricalEventTime(h)
            )
            currentPoints.add(tp)
        }
        val tp = motionEventToTouchPoint(event, 0)
        currentPoints.add(tp)
    }

    private fun motionEventToTouchPoint(event: MotionEvent, pointerIndex: Int): TouchPoint {
        val tiltAngle = event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
        val orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex)
        val tx = (Math.sin(orientation.toDouble()) * Math.sin(tiltAngle.toDouble()) * 1000).toInt()
        val ty = (Math.cos(orientation.toDouble()) * Math.sin(tiltAngle.toDouble()) * 1000).toInt()
        return TouchPoint(
            event.getX(pointerIndex),
            event.getY(pointerIndex),
            event.getPressure(pointerIndex),
            event.getSize(pointerIndex),
            tx, ty,
            event.eventTime
        )
    }
}
