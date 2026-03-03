package com.wyldsoft.notes.sdkintegration.onyx

import android.graphics.PointF
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.Tool
import com.wyldsoft.notes.shapemanagement.EraseManager
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.actions.TransformType
import com.wyldsoft.notes.shapemanagement.DrawManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.shapemanagement.TransformMode

/**
 * Handles all stylus-related operations for Onyx devices:
 * Drawing:
 *   - Starts and ends drawing sessions
 *   - Processes touch points to create shapes
 *   - Manages scribble rendering to bitmap
 * Erasing:
 *   - Starts and ends erasing sessions
 *   - Processes touch points to find and erase shapes
 *   - Manages partial refresh of erased areas
 * Shape Management:
 *   - Stores drawn shapes
 *   - Converts touch points to NoteCoordinates
 *   - Renders shapes to bitmap
 * This class encapsulates the logic for processing stylus input and managing shapes.
 */
class OnyxStylusHandler(
    protected var surfaceView: SurfaceView,
    private val viewModel: EditorViewModel,
    private val rxManager: RxManager,
    private val bitmapManager: BitmapManager,
    private val shapesManager: ShapesManager,
    private val onDrawingStateChanged: (isDrawing: Boolean) -> Unit, // if false then enableFingerTouch and force screen refresh. If true then disableFingerTouch
    private val onShapeCompleted: (id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long>) -> Unit, //
    private val onShapeRemoved: (shapeId: String) -> Unit,
    private val onSetRawDrawingRenderEnabled: (Boolean) -> Unit, // toggle SDK stroke rendering on/off
    private val onForceScreenRefresh: () -> Unit // force e-ink display refresh
) {
    companion object {
        private const val TAG = "OnyxStylusHandler"
    }

    init {
        Log.d(TAG, "NEW OnyxStylusHandler")
    }

    private var drawManager = DrawManager(bitmapManager, onShapeCompleted)

    // Erase management
    private val eraseManager = EraseManager(surfaceView, rxManager, bitmapManager, onShapeRemoved)

    // Selection management
    private val selectionManager get() = viewModel.selectionManager
    // Snapshot of domain shapes before a transform, for undo
    private var preTransformShapeSnapshots: List<com.wyldsoft.notes.domain.models.Shape>? = null
    // Bounding box center at time of transform start (for persist/undo)
    private var transformCenterX: Float = 0f
    private var transformCenterY: Float = 0f

    private var refreshCount: Int = 0
    private var REFRESH_COUNT_LIMIT: Int = 100

    // Drawing state
    private var isDrawingInProgress = false
    private var isErasingInProgress = false
    private var selectionCancelledThisStroke = false
    private var currentPenProfile: PenProfile = PenProfile.getDefaultProfile(PenType.BALLPEN)

    /**
     * Returns true if erasing is currently in progress
     */
    fun isErasing(): Boolean = isErasingInProgress
    
    /**
     * Updates the current pen profile used for drawing
     */
    fun updatePenProfile(penProfile: PenProfile) {
        currentPenProfile = penProfile
        drawManager.updatePenProfile(penProfile)
    }

    /**
     * Creates the Onyx callback for handling stylus input.
     * The callback interfaces with the Onyx SDK to receive raw input events from the stylus.
     */
    fun createOnyxCallback(): RawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            val tool = viewModel.uiState.value.selectedTool
            if (tool == Tool.SELECTOR) {
                onDrawingStateChanged(true)
                handleSelectionBegin(touchPoint)
                return
            }
            isDrawingInProgress = true
            onDrawingStateChanged(true)
            viewModel.startDrawing()
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            isDrawingInProgress = false
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            Log.d("OnyxStylusHandler", "Refresh count: $refreshCount")
            if (refreshCount < REFRESH_COUNT_LIMIT) {
                refreshCount++
                return
            }
            refreshCount = 0

            if (touchPoint == null) return
            val tool = viewModel.uiState.value.selectedTool
            if (tool != Tool.SELECTOR) return
            if (!selectionManager.hasSelection) return

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

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList?) {
            // If selection was cancelled during this stroke, discard the points
            if (selectionCancelledThisStroke) {
                selectionCancelledThisStroke = false
                onDrawingStateChanged(false)
                return
            }
            val tool = viewModel.uiState.value.selectedTool
            if (tool == Tool.SELECTOR) {
                touchPointList?.let { handleSelectionInput(it) }
                onDrawingStateChanged(false)
                return
            }
            touchPointList?.points?.let { points ->
                val notePointList = convertTouchPointListToNoteCoordinates(touchPointList)
                val newShape = drawManager.newShape(notePointList)
                shapesManager.addShape(newShape)
            }
            // moved from onEndRawDraing
            isDrawingInProgress = false
            onDrawingStateChanged(false)
            viewModel.endDrawing()
        }

        override fun onBeginRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            isErasingInProgress = true
            viewModel.startErasing()
            Log.d(TAG, "Erasing started")
        }

        override fun onEndRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            isErasingInProgress = false
            viewModel.endErasing()
            Log.d(TAG, "Erasing ended")
        }

        override fun onRawErasingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            // Handle erase move
        }

        override fun onRawErasingTouchPointListReceived(touchPointList: TouchPointList?) {
            touchPointList?.let { erasePointList ->
                val noteErasePointList = convertTouchPointListToNoteCoordinates(erasePointList)
                eraseManager.handleErasing(noteErasePointList, shapesManager)
            }
        }
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
            // Priority: rotation handle > scale handle > inside box (move) > outside box (cancel)
            if (selectionManager.isOnRotationHandle(notePoint.x, notePoint.y)) {
                onSetRawDrawingRenderEnabled(false)
                preTransformShapeSnapshots = snapshotSelectedShapes()
                recordTransformCenter()
                selectionManager.beginRotate(notePoint)
                Log.d(TAG, "Selection: rotate started")
            } else {
                val cornerIndex = selectionManager.isOnScaleHandle(notePoint.x, notePoint.y)
                if (cornerIndex != null) {
                    onSetRawDrawingRenderEnabled(false)
                    preTransformShapeSnapshots = snapshotSelectedShapes()
                    recordTransformCenter()
                    selectionManager.beginScale(cornerIndex, notePoint)
                    Log.d(TAG, "Selection: scale started from corner $cornerIndex")
                } else if (selectionManager.isInsideBoundingBox(notePoint.x, notePoint.y)) {
                    // Start dragging selected shapes
                    onSetRawDrawingRenderEnabled(false)
                    preTransformShapeSnapshots = snapshotSelectedShapes()
                    selectionManager.beginDrag(notePoint)
                    Log.d(TAG, "Selection: drag started")
                } else {
                    // Touch outside bounding box - exit selection mode
                    selectionCancelledThisStroke = true
                    viewModel.cancelSelection()
                    onForceScreenRefresh()
                    Log.d(TAG, "Selection: cancelled, exiting selection mode")
                }
            }
        } else {
            // No selection yet - start new lasso
            onSetRawDrawingRenderEnabled(true)
            selectionManager.beginLasso()
            Log.d(TAG, "Selection: lasso started")
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
                    if (selectionManager.hasSelection) {
                        onSetRawDrawingRenderEnabled(false)
                    }
                    onForceScreenRefresh()
                }
            }
        }
    }

    /**
     * Converts a TouchPointList from SurfaceViewCoordinates to NoteCoordinates
     * using the current ViewportManager transformation.
     */
    private fun convertTouchPointListToNoteCoordinates(surfacePointList: TouchPointList): TouchPointList {
        val notePointList = TouchPointList()
        val viewportManager = viewModel.viewportManager
        
        for (i in 0 until surfacePointList.size()) {
            val tp = surfacePointList.get(i)
            // Convert from SurfaceViewCoordinates to NoteCoordinates
            val notePoint = viewportManager.surfaceToNoteCoordinates(tp.x, tp.y)
            val noteTouchPoint = TouchPoint(
                notePoint.x,
                notePoint.y,
                tp.pressure,
                tp.size,
                tp.timestamp
            )
            notePointList.add(noteTouchPoint)
        }
        
        return notePointList
    }


    /**
     * Clears all drawings
     */
    fun clearDrawing() {
        shapesManager.clear()
        bitmapManager.clearDrawing()
    }

}
