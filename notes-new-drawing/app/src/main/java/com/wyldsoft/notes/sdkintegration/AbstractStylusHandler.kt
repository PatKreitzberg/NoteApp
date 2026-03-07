package com.wyldsoft.notes.sdkintegration

import android.graphics.PointF
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.actions.TransformType
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.geometry.GeometryShapeCalculator
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.Tool
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.DrawManager
import com.wyldsoft.notes.shapemanagement.EraseManager
import com.wyldsoft.notes.shapemanagement.ShapeFactory
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.shapemanagement.TransformMode

/**
 * Base class for stylus handlers, containing shared drawing, erasing,
 * and selection logic. Subclasses wire up device-specific input events
 * (Onyx SDK callbacks vs standard Android MotionEvents) and override
 * hooks for SDK-specific behavior.
 */
abstract class AbstractStylusHandler(
    protected val surfaceView: SurfaceView,
    protected val viewModel: EditorViewModel,
    protected val bitmapManager: BitmapManager,
    protected val shapesManager: ShapesManager,
    protected val onDrawingStateChanged: (isDrawing: Boolean) -> Unit,
    protected val onShapeCompleted: (id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long>) -> Unit,
    protected val onShapeRemoved: (shapeId: String) -> Unit,
    protected val onForceScreenRefresh: () -> Unit,
    rxManager: RxManager? = null
) {
    protected var drawManager = DrawManager(bitmapManager, onShapeCompleted)
    protected val eraseManager = EraseManager(surfaceView, rxManager, bitmapManager, onShapeRemoved)

    protected val selectionManager get() = viewModel.selectionManager
    protected var preTransformShapeSnapshots: List<Shape>? = null
    protected var transformCenterX: Float = 0f
    protected var transformCenterY: Float = 0f

    protected var isDrawingInProgress = false
    protected var isErasingInProgress = false
    protected var isGeometryDrawingInProgress = false
    protected var geometryStartNoteX = 0f
    protected var geometryStartNoteY = 0f
    protected var selectionCancelledThisStroke = false
    protected var currentPenProfile: PenProfile = PenProfile.getDefaultProfile(PenType.BALLPEN)

    protected var refreshCount: Int = 0
    protected val REFRESH_COUNT_LIMIT: Int = 100

    fun isErasing(): Boolean = isErasingInProgress

    fun updatePenProfile(penProfile: PenProfile) {
        currentPenProfile = penProfile
        drawManager.updatePenProfile(penProfile)
    }

    fun clearDrawing() {
        shapesManager.clear()
        bitmapManager.clearDrawing()
    }

    // --- Hooks for SDK-specific behavior (no-ops by default) ---

    /** Called when a selection transform (drag/scale/rotate) begins. */
    protected open fun onSelectionTransformStarted() {}

    /** Called when a lasso selection completes and shapes are selected. */
    protected open fun onLassoSelectionCompleted() {}

    /** Called when starting a new lasso (no existing selection). */
    protected open fun onLassoStarted() {}

    // --- Shared drawing state transitions ---

    protected fun beginDrawing() {
        isDrawingInProgress = true
        onDrawingStateChanged(true)
        viewModel.startDrawing()
    }

    protected fun beginSelectionStroke(touchPoint: TouchPoint?) {
        onDrawingStateChanged(true)
        handleSelectionBegin(touchPoint)
    }

    /**
     * Finalizes a completed stroke by creating a shape and adding it to the manager.
     * Called by subclasses once they have a complete TouchPointList.
     */
    protected fun finalizeStroke(touchPointList: TouchPointList) {
        touchPointList.points?.let {
            val notePointList = convertTouchPointListToNoteCoordinates(touchPointList)
            val newShape = drawManager.newShape(notePointList)
            shapesManager.addShape(newShape)
        }
        isDrawingInProgress = false
        onDrawingStateChanged(false)
        viewModel.endDrawing()
    }

    /**
     * Handles the end of a stroke when selection was cancelled during it.
     * Returns true if the stroke was cancelled (caller should return early).
     */
    protected fun handleCancelledStroke(): Boolean {
        if (selectionCancelledThisStroke) {
            selectionCancelledThisStroke = false
            onDrawingStateChanged(false)
            return true
        }
        return false
    }

    /**
     * Handles the end of a stroke when the selector tool is active.
     * Returns true if handled (caller should return early).
     */
    protected fun handleSelectorStrokeEnd(touchPointList: TouchPointList): Boolean {
        val tool = viewModel.uiState.value.selectedTool
        if (tool == Tool.SELECTOR) {
            handleSelectionInput(touchPointList)
            onDrawingStateChanged(false)
            return true
        }
        return false
    }

    // --- Geometry shape drawing ---

    protected fun beginGeometryDrawing(touchPoint: TouchPoint) {
        val notePoint = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)
        geometryStartNoteX = notePoint.x
        geometryStartNoteY = notePoint.y
        isGeometryDrawingInProgress = true
        isDrawingInProgress = true
        bitmapManager.beginGeometryDrawing()
        onDrawingStateChanged(true)
        viewModel.startDrawing()
    }

    protected fun updateGeometryPreview(touchPoint: TouchPoint) {
        if (!isGeometryDrawingInProgress) return
        val noteEnd = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)
        val shapeType = viewModel.uiState.value.selectedGeometricShape
        val notePoints = GeometryShapeCalculator.calculate(
            shapeType, geometryStartNoteX, geometryStartNoteY, noteEnd.x, noteEnd.y
        )
        bitmapManager.drawGeometryPreview(notePoints, currentPenProfile)
    }

    protected fun finalizeGeometryShape(touchPointList: TouchPointList) {
        if (!isGeometryDrawingInProgress) return

        val lastPoint = touchPointList.points?.lastOrNull()
        if (lastPoint != null) {
            val noteEnd = viewModel.viewportManager.surfaceToNoteCoordinates(lastPoint.x, lastPoint.y)
            val geometricShapeType = viewModel.uiState.value.selectedGeometricShape

            // Compute outline points in note coordinates
            val notePoints = GeometryShapeCalculator.calculate(
                geometricShapeType, geometryStartNoteX, geometryStartNoteY, noteEnd.x, noteEnd.y
            )

            // Build a TouchPointList from the note-coordinate outline points
            val shapePointList = TouchPointList()
            val now = System.currentTimeMillis()
            notePoints.forEach { pt ->
                shapePointList.add(TouchPoint(pt.x, pt.y, 1.0f, 1.0f, now))
            }

            // Create a BaseShape using current pen type so it renders correctly
            val sdkShapeType = ShapesManager.penTypeToShapeType(currentPenProfile.penType)
            val baseShape = ShapeFactory.createShape(sdkShapeType).apply {
                setTouchPointList(shapePointList)
                setStrokeColor(currentPenProfile.getColorAsInt())
                setStrokeWidth(currentPenProfile.strokeWidth)
                setShapeType(sdkShapeType)
            }
            ShapesManager.applyCharcoalTexture(baseShape, currentPenProfile.penType)
            baseShape.updateShapeRect()

            // Add to in-memory manager so it appears on next full refresh
            shapesManager.addShape(baseShape)

            // Persist and record undo action using the matching ID
            val domainShape = Shape(
                id = baseShape.id,
                type = geometricShapeType.toDomainShapeType(),
                points = notePoints,
                strokeWidth = currentPenProfile.strokeWidth,
                strokeColor = currentPenProfile.getColorAsInt(),
                penType = currentPenProfile.penType
            )
            viewModel.addGeometricShape(domainShape)
        }

        bitmapManager.endGeometryDrawing()
        isGeometryDrawingInProgress = false
        isDrawingInProgress = false
        onDrawingStateChanged(false)
        viewModel.endDrawing()
        onForceScreenRefresh()
    }

    // --- Shared erasing state transitions ---

    protected fun beginErasing() {
        isErasingInProgress = true
        viewModel.startErasing()
    }

    protected fun finalizeErase(noteErasePointList: TouchPointList) {
        eraseManager.handleErasing(noteErasePointList, shapesManager)
    }

    protected fun endErasing() {
        isErasingInProgress = false
        viewModel.endErasing()
        onForceScreenRefresh()
    }

    // --- Shared selection move/update dispatch ---

    protected fun handleSelectionMoveUpdate(touchPoint: TouchPoint) {
        val notePoint = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)
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
    }

    // --- Selection handling ---

    protected fun snapshotSelectedShapes(): List<Shape> {
        return viewModel.currentNote.value.shapes
            .filter { it.id in selectionManager.selectedShapeIds }
    }

    protected fun recordTransformCenter() {
        val box = selectionManager.selectionBoundingBox ?: return
        transformCenterX = box.centerX()
        transformCenterY = box.centerY()
    }

    protected fun handleSelectionBegin(touchPoint: TouchPoint?) {
        if (touchPoint == null) return
        val notePoint = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)

        if (selectionManager.hasSelection) {
            if (selectionManager.isOnRotationHandle(notePoint.x, notePoint.y)) {
                onSelectionTransformStarted()
                preTransformShapeSnapshots = snapshotSelectedShapes()
                recordTransformCenter()
                selectionManager.beginRotate(notePoint)
            } else {
                val cornerIndex = selectionManager.isOnScaleHandle(notePoint.x, notePoint.y)
                if (cornerIndex != null) {
                    onSelectionTransformStarted()
                    preTransformShapeSnapshots = snapshotSelectedShapes()
                    recordTransformCenter()
                    selectionManager.beginScale(cornerIndex, notePoint)
                } else if (selectionManager.isInsideBoundingBox(notePoint.x, notePoint.y)) {
                    onSelectionTransformStarted()
                    preTransformShapeSnapshots = snapshotSelectedShapes()
                    selectionManager.beginDrag(notePoint)
                } else {
                    selectionCancelledThisStroke = true
                    viewModel.cancelSelection()
                    onForceScreenRefresh()
                }
            }
        } else {
            onLassoStarted()
            selectionManager.beginLasso()
        }
    }

    protected fun handleSelectionInput(touchPointList: TouchPointList) {
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
                    if (selectionManager.hasSelection) {
                        onLassoSelectionCompleted()
                    }
                    onForceScreenRefresh()
                }
            }
        }
    }

    protected fun convertTouchPointListToNoteCoordinates(surfacePointList: TouchPointList): TouchPointList {
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
}
