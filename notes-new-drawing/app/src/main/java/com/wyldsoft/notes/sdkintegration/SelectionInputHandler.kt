package com.wyldsoft.notes.sdkintegration

import android.graphics.PointF
import android.graphics.RectF
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.actions.TransformType
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.shapemanagement.TransformMode
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.utils.surfacePointsToNoteTouchPoints

/**
 * Handles lasso + drag/scale/rotate input for selection mode.
 * Extracted from AbstractStylusHandler to keep it under 300 lines.
 */
private const val TAP_MAX_MOVEMENT_PX = 20f
private const val TAP_HIT_RADIUS = 30f

class SelectionInputHandler(
    private val viewModel: EditorViewModel,
    private val bitmapManager: BitmapManager,
    private val getShapesManager: () -> ShapesManager,
    private val onSelectionTransformStarted: () -> Unit,
    private val onLassoStarted: () -> Unit,
    private val onLassoSelectionCompleted: () -> Unit,
    private val onForceScreenRefresh: () -> Unit
) {
    private val shapesManager: ShapesManager get() = getShapesManager()
    private val selectionManager get() = viewModel.selectionManager
    private var preTransformShapeSnapshots: List<Shape>? = null
    private var preTransformBoundingBox: RectF? = null
    private var transformCenterX: Float = 0f
    private var transformCenterY: Float = 0f

    var wasCancelled = false
        private set

    fun clearCancelled() { wasCancelled = false }

    fun handleMoveUpdate(touchPoint: TouchPoint) {
        val notePoint = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)
        val currentPoint = PointF(notePoint.x, notePoint.y)
        when {
            selectionManager.isDragging -> {
                val oldBBox = selectionManager.selectionBoundingBox?.let { RectF(it) }
                selectionManager.updateDrag(currentPoint, shapesManager.shapes())
                doPartialRefresh(oldBBox, selectionManager.selectionBoundingBox)
            }
            selectionManager.transformMode == TransformMode.SCALE -> {
                val oldBBox = selectionManager.selectionBoundingBox?.let { RectF(it) }
                selectionManager.updateScale(currentPoint, shapesManager.shapes())
                doPartialRefresh(oldBBox, selectionManager.selectionBoundingBox)
            }
            selectionManager.transformMode == TransformMode.ROTATE -> {
                val oldBBox = selectionManager.selectionBoundingBox?.let { RectF(it) }
                selectionManager.updateRotate(currentPoint, shapesManager.shapes())
                doPartialRefresh(oldBBox, selectionManager.selectionBoundingBox)
            }
        }
    }

    fun handleBegin(touchPoint: TouchPoint?) {
        if (touchPoint == null) return
        val notePoint = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)

        if (selectionManager.hasSelection) {
            when {
                selectionManager.isOnRotationHandle(notePoint.x, notePoint.y) -> {
                    startTransform()
                    selectionManager.beginRotate(notePoint)
                }
                selectionManager.isOnScaleHandle(notePoint.x, notePoint.y).also {
                    if (it != null) { startTransform(); selectionManager.beginScale(it, notePoint) }
                } != null -> { /* handled above */ }
                selectionManager.isInsideBoundingBox(notePoint.x, notePoint.y) -> {
                    startTransform()
                    selectionManager.beginDrag(notePoint)
                }
                else -> {
                    val oldBBox = selectionManager.selectionBoundingBox?.let { RectF(it) }
                    wasCancelled = true
                    viewModel.cancelSelection()
                    doPartialRefresh(oldBBox, null, drawOverlay = false)
                }
            }
        } else {
            onLassoStarted()
            selectionManager.beginLasso()
        }
    }

    fun handleEnd(touchPointList: TouchPointList) {
        val notePointList = surfacePointsToNoteTouchPoints(touchPointList, viewModel.viewportManager)

        when (selectionManager.transformMode) {
            TransformMode.SCALE -> {
                val scaleFactor = selectionManager.finishScale(notePointList, shapesManager.shapes())
                if (scaleFactor != null) {
                    preTransformShapeSnapshots?.let { originals ->
                        viewModel.recordTransformAction(originals, TransformType.SCALE, scaleFactor, transformCenterX, transformCenterY)
                    }
                    viewModel.persistScaledShapes(selectionManager.selectedShapeIds, scaleFactor, transformCenterX, transformCenterY)
                }
                val preBBox = preTransformBoundingBox.also { preTransformShapeSnapshots = null; preTransformBoundingBox = null }
                doPartialRefresh(preBBox, selectionManager.selectionBoundingBox)
            }
            TransformMode.ROTATE -> {
                val angleRad = selectionManager.finishRotate(notePointList, shapesManager.shapes())
                if (angleRad != null) {
                    preTransformShapeSnapshots?.let { originals ->
                        viewModel.recordTransformAction(originals, TransformType.ROTATE, angleRad, transformCenterX, transformCenterY)
                    }
                    viewModel.persistRotatedShapes(selectionManager.selectedShapeIds, angleRad, transformCenterX, transformCenterY)
                }
                val preBBox = preTransformBoundingBox.also { preTransformShapeSnapshots = null; preTransformBoundingBox = null }
                doPartialRefresh(preBBox, selectionManager.selectionBoundingBox)
            }
            TransformMode.MOVE, TransformMode.NONE -> {
                if (selectionManager.isDragging) {
                    val delta = selectionManager.finishDrag(notePointList, shapesManager.shapes())
                    if (delta != null) {
                        preTransformShapeSnapshots?.let { viewModel.recordMoveAction(it, delta.x, delta.y) }
                        viewModel.persistMovedShapes(selectionManager.selectedShapeIds, delta.x, delta.y)
                    }
                    val preBBox = preTransformBoundingBox.also { preTransformShapeSnapshots = null; preTransformBoundingBox = null }
                    doPartialRefresh(preBBox, selectionManager.selectionBoundingBox)
                } else if (selectionManager.isLassoInProgress) {
                    if (isTap(notePointList)) {
                        selectionManager.clearSelection()
                        val first = notePointList.get(0)
                        val hit = findShapeAtPoint(first.x, first.y)
                        if (hit != null) {
                            hit.updateShapeRect()
                            val bbox = hit.boundingRect ?: RectF(first.x, first.y, first.x, first.y)
                            selectionManager.setSelection(setOf(hit.id), bbox)
                        }
                        viewModel.notifySelectionChanged()
                        if (selectionManager.hasSelection) onLassoSelectionCompleted()
                        onForceScreenRefresh()
                    } else {
                        selectionManager.addLassoPoints(notePointList)
                        selectionManager.finishLasso(shapesManager.shapes(), viewModel.activeLayer.value)
                        viewModel.notifySelectionChanged()
                        if (selectionManager.hasSelection) onLassoSelectionCompleted()
                        onForceScreenRefresh()
                    }
                }
            }
        }
    }

    fun doPartialRefresh(oldBBoxNote: RectF?, newBBoxNote: RectF?, drawOverlay: Boolean = true) {
        val dirtyNote = when {
            oldBBoxNote != null && newBBoxNote != null -> RectF(oldBBoxNote).apply { union(newBBoxNote) }
            oldBBoxNote != null -> RectF(oldBBoxNote)
            newBBoxNote != null -> RectF(newBBoxNote)
            else -> { onForceScreenRefresh(); return }
        }
        bitmapManager.partialRefresh(dirtyNote, shapesManager.shapes(), if (drawOverlay) selectionManager else null)
    }

    private fun startTransform() {
        onSelectionTransformStarted()
        preTransformShapeSnapshots = viewModel.currentNote.value.shapes.filter { it.id in selectionManager.selectedShapeIds }
        preTransformBoundingBox = selectionManager.selectionBoundingBox?.let { RectF(it) }
        val box = selectionManager.selectionBoundingBox
        if (box != null) { transformCenterX = box.centerX(); transformCenterY = box.centerY() }
    }

    private fun isTap(notePointList: TouchPointList): Boolean {
        if (notePointList.size() == 0) return false
        val first = notePointList.get(0)
        val threshold = TAP_MAX_MOVEMENT_PX * TAP_MAX_MOVEMENT_PX
        for (i in 0 until notePointList.size()) {
            val p = notePointList.get(i)
            val dx = p.x - first.x
            val dy = p.y - first.y
            if (dx * dx + dy * dy > threshold) return false
        }
        return true
    }

    private fun findShapeAtPoint(noteX: Float, noteY: Float): BaseShape? {
        val tpl = TouchPointList()
        tpl.add(TouchPoint(noteX, noteY, 1f, 1f, System.currentTimeMillis()))
        return shapesManager.shapes().asReversed().firstOrNull { it.hitTestPoints(tpl, TAP_HIT_RADIUS) }
    }
}
