package com.wyldsoft.notes.sdkintegration.generic

import android.graphics.PointF
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.Tool
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.DrawManager
import com.wyldsoft.notes.utils.createStrokePaint
import com.wyldsoft.notes.shapemanagement.EraseManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.shapemanagement.TransformMode
import com.wyldsoft.notes.actions.TransformType

/**
 * Handles stylus input on non-Onyx devices using standard Android MotionEvent.
 * Processes TOOL_TYPE_STYLUS, TOOL_TYPE_ERASER, and TOOL_TYPE_FINGER events.
 */
class GenericStylusHandler(
    private val surfaceView: SurfaceView,
    private val viewModel: EditorViewModel,
    private val bitmapManager: BitmapManager,
    private val shapesManager: ShapesManager,
    private val onDrawingStateChanged: (isDrawing: Boolean) -> Unit,
    private val onShapeCompleted: (id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long>) -> Unit,
    private val onShapeRemoved: (shapeId: String) -> Unit,
    private val onForceScreenRefresh: () -> Unit
) {
    companion object {
        private const val TAG = "GenericStylusHandler"
    }

    private var drawManager = DrawManager(bitmapManager, onShapeCompleted)
    private val eraseManager = EraseManager(surfaceView, bitmapManager, onShapeRemoved)

    private val selectionManager get() = viewModel.selectionManager
    private var preTransformShapeSnapshots: List<com.wyldsoft.notes.domain.models.Shape>? = null
    private var transformCenterX: Float = 0f
    private var transformCenterY: Float = 0f

    private var isDrawingInProgress = false
    private var isErasingInProgress = false
    private var selectionCancelledThisStroke = false
    private var currentPenProfile: PenProfile = PenProfile.getDefaultProfile(PenType.BALLPEN)

    // Accumulate points during a stroke
    private val currentPoints = mutableListOf<TouchPoint>()
    // Track how many points have been rendered incrementally
    private var lastRenderedPointIndex = 0
    private var refreshCount: Int = 0
    private val REFRESH_COUNT_LIMIT: Int = 100

    fun isErasing(): Boolean = isErasingInProgress

    fun updatePenProfile(penProfile: PenProfile) {
        currentPenProfile = penProfile
        drawManager.updatePenProfile(penProfile)
    }

    /**
     * Process a MotionEvent from the SurfaceView's touch listener.
     * Returns true if the event was consumed (stylus/eraser).
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val toolType = event.getToolType(0)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS
        val isEraser = toolType == MotionEvent.TOOL_TYPE_ERASER
        val isFinger = toolType == MotionEvent.TOOL_TYPE_FINGER

        // On non-Onyx devices, treat finger as stylus for drawing
        if (!isStylus && !isEraser && !isFinger) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isEraser) {
                    handleEraseBegin()
                } else {
                    handleDrawBegin(event)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isEraser || isErasingInProgress) {
                    handleEraseMove(event)
                } else {
                    handleDrawMove(event)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isEraser || isErasingInProgress) {
                    handleEraseEnd()
                } else {
                    handleDrawEnd()
                }
            }
        }
        return true
    }

    // --- Drawing ---

    private fun handleDrawBegin(event: MotionEvent) {
        val tool = viewModel.uiState.value.selectedTool
        if (tool == Tool.SELECTOR) {
            onDrawingStateChanged(true)
            val tp = motionEventToTouchPoint(event, 0)
            handleSelectionBegin(tp)
            return
        }
        isDrawingInProgress = true
        onDrawingStateChanged(true)
        viewModel.startDrawing()
        currentPoints.clear()
        lastRenderedPointIndex = 0
        addPointsFromEvent(event)
    }

    private fun handleDrawMove(event: MotionEvent) {
        val tool = viewModel.uiState.value.selectedTool
        if (tool == Tool.SELECTOR && selectionManager.hasSelection) {
            val isTransforming = selectionManager.isDragging ||
                selectionManager.transformMode == TransformMode.SCALE ||
                selectionManager.transformMode == TransformMode.ROTATE

            // Only throttle non-transform operations (e.g. lasso).
            // Drag/scale/rotate need every event for responsive feedback.
            if (!isTransforming) {
                refreshCount++
                if (refreshCount < REFRESH_COUNT_LIMIT) return
                refreshCount = 0
            }

            val tp = motionEventToTouchPoint(event, event.pointerCount - 1)
            val notePoint = viewModel.viewportManager.surfaceToNoteCoordinates(tp.x, tp.y)
            val currentPoint = PointF(notePoint.x, notePoint.y)

            when {
                selectionManager.isDragging -> {
                    selectionManager.updateDrag(currentPoint, shapesManager.shapes())
                    onForceScreenRefresh()
                }
                selectionManager.transformMode == TransformMode.SCALE -> {
                    selectionManager.updateScale(currentPoint, shapesManager.shapes())
                    onForceScreenRefresh()
                }
                selectionManager.transformMode == TransformMode.ROTATE -> {
                    selectionManager.updateRotate(currentPoint, shapesManager.shapes())
                    onForceScreenRefresh()
                }
            }
            return
        }
        // Accumulate points using historical data for accuracy
        addPointsFromEvent(event)
        // Draw new segments incrementally to give real-time feedback
        drawIncrementalSegments()
    }

    private fun handleDrawEnd() {
        if (selectionCancelledThisStroke) {
            selectionCancelledThisStroke = false
            onDrawingStateChanged(false)
            currentPoints.clear()
            return
        }
        val tool = viewModel.uiState.value.selectedTool
        if (tool == Tool.SELECTOR) {
            val touchPointList = TouchPointList()
            currentPoints.forEach { touchPointList.add(it) }
            handleSelectionInput(touchPointList)
            onDrawingStateChanged(false)
            currentPoints.clear()
            return
        }
        if (currentPoints.isNotEmpty()) {
            val touchPointList = TouchPointList()
            currentPoints.forEach { touchPointList.add(it) }
            val notePointList = convertTouchPointListToNoteCoordinates(touchPointList)
            val newShape = drawManager.newShape(notePointList)
            shapesManager.addShape(newShape)
        }
        isDrawingInProgress = false
        onDrawingStateChanged(false)
        viewModel.endDrawing()
        currentPoints.clear()
    }

    // --- Erasing ---

    private fun handleEraseBegin() {
        isErasingInProgress = true
        viewModel.startErasing()
        currentPoints.clear()
        Log.d(TAG, "Erasing started")
    }

    private fun handleEraseMove(event: MotionEvent) {
        addPointsFromEvent(event)
    }

    private fun handleEraseEnd() {
        if (currentPoints.isNotEmpty()) {
            val touchPointList = TouchPointList()
            currentPoints.forEach { touchPointList.add(it) }
            val noteErasePointList = convertTouchPointListToNoteCoordinates(touchPointList)
            eraseManager.handleErasing(noteErasePointList, shapesManager)
        }
        isErasingInProgress = false
        viewModel.endErasing()
        currentPoints.clear()
        Log.d(TAG, "Erasing ended")
    }

    // --- Selection handling ---

    private fun snapshotSelectedShapes(): List<com.wyldsoft.notes.domain.models.Shape> {
        return viewModel.currentNote.value.shapes
            .filter { it.id in selectionManager.selectedShapeIds }
    }

    private fun recordTransformCenter() {
        val box = selectionManager.selectionBoundingBox ?: return
        transformCenterX = box.centerX()
        transformCenterY = box.centerY()
    }

    private fun handleSelectionBegin(touchPoint: TouchPoint?) {
        if (touchPoint == null) return
        val notePoint = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)

        if (selectionManager.hasSelection) {
            if (selectionManager.isOnRotationHandle(notePoint.x, notePoint.y)) {
                preTransformShapeSnapshots = snapshotSelectedShapes()
                recordTransformCenter()
                selectionManager.beginRotate(notePoint)
            } else {
                val cornerIndex = selectionManager.isOnScaleHandle(notePoint.x, notePoint.y)
                if (cornerIndex != null) {
                    preTransformShapeSnapshots = snapshotSelectedShapes()
                    recordTransformCenter()
                    selectionManager.beginScale(cornerIndex, notePoint)
                } else if (selectionManager.isInsideBoundingBox(notePoint.x, notePoint.y)) {
                    preTransformShapeSnapshots = snapshotSelectedShapes()
                    selectionManager.beginDrag(notePoint)
                } else {
                    selectionCancelledThisStroke = true
                    viewModel.cancelSelection()
                    onForceScreenRefresh()
                }
            }
        } else {
            selectionManager.beginLasso()
        }
    }

    private fun handleSelectionInput(touchPointList: TouchPointList) {
        val notePointList = convertTouchPointListToNoteCoordinates(touchPointList)

        when (selectionManager.transformMode) {
            TransformMode.SCALE -> {
                val scaleFactor = selectionManager.finishScale(notePointList, shapesManager.shapes())
                if (scaleFactor != null) {
                    preTransformShapeSnapshots?.let { originals ->
                        viewModel.recordTransformAction(
                            originals, TransformType.SCALE, scaleFactor,
                            transformCenterX, transformCenterY
                        )
                    }
                    viewModel.persistScaledShapes(
                        selectionManager.selectedShapeIds, scaleFactor,
                        transformCenterX, transformCenterY
                    )
                }
                preTransformShapeSnapshots = null
                onForceScreenRefresh()
            }
            TransformMode.ROTATE -> {
                val angleRad = selectionManager.finishRotate(notePointList, shapesManager.shapes())
                if (angleRad != null) {
                    preTransformShapeSnapshots?.let { originals ->
                        viewModel.recordTransformAction(
                            originals, TransformType.ROTATE, angleRad,
                            transformCenterX, transformCenterY
                        )
                    }
                    viewModel.persistRotatedShapes(
                        selectionManager.selectedShapeIds, angleRad,
                        transformCenterX, transformCenterY
                    )
                }
                preTransformShapeSnapshots = null
                onForceScreenRefresh()
            }
            TransformMode.MOVE, TransformMode.NONE -> {
                if (selectionManager.isDragging) {
                    val delta = selectionManager.finishDrag(notePointList, shapesManager.shapes())
                    if (delta != null) {
                        preTransformShapeSnapshots?.let { originals ->
                            viewModel.recordMoveAction(originals, delta.x, delta.y)
                        }
                        viewModel.persistMovedShapes(selectionManager.selectedShapeIds, delta.x, delta.y)
                    }
                    preTransformShapeSnapshots = null
                    onForceScreenRefresh()
                } else if (selectionManager.isLassoInProgress) {
                    selectionManager.addLassoPoints(notePointList)
                    selectionManager.finishLasso(shapesManager.shapes())
                    onForceScreenRefresh()
                }
            }
        }
    }

    // --- Incremental drawing ---

    /**
     * Draws newly added line segments to the bitmap and pushes to screen.
     * This provides real-time visual feedback as the user draws.
     */
    private fun drawIncrementalSegments() {
        val startIdx = if (lastRenderedPointIndex > 0) lastRenderedPointIndex - 1 else 0
        if (currentPoints.size < 2 || startIdx >= currentPoints.size - 1) return

        bitmapManager.drawSegmentsToScreen(currentPoints, startIdx, currentPenProfile)
        lastRenderedPointIndex = currentPoints.size
    }

    // --- Helpers ---

    private fun addPointsFromEvent(event: MotionEvent) {
        // Include historical points for accuracy
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

    private fun convertTouchPointListToNoteCoordinates(surfacePointList: TouchPointList): TouchPointList {
        val notePointList = TouchPointList()
        val viewportManager = viewModel.viewportManager
        for (i in 0 until surfacePointList.size()) {
            val tp = surfacePointList.get(i)
            val notePoint = viewportManager.surfaceToNoteCoordinates(tp.x, tp.y)
            val noteTouchPoint = TouchPoint(notePoint.x, notePoint.y, tp.pressure, tp.size, tp.timestamp)
            notePointList.add(noteTouchPoint)
        }
        return notePointList
    }

    fun clearDrawing() {
        shapesManager.clear()
        bitmapManager.clearDrawing()
    }
}
