package com.wyldsoft.notes.sdkintegration

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
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
 * Owns the "auto-return-to-draw" policy: touching outside the selection
 * cancels it and switches back to Draw mode on stylus lift.
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
    /** Result of [handleBegin] so callers know what happened without mutable flags. */
    sealed class BeginResult {
        data object TransformStarted : BeginResult()
        data object LassoStarted : BeginResult()
        /** Touch was outside selection — selection cleared, will auto-return to Draw on lift. */
        data object Cancelled : BeginResult()
        data object Ignored : BeginResult()
    }

    private val shapesManager: ShapesManager get() = getShapesManager()
    private val selectionManager get() = viewModel.selectionManager
    private var preTransformShapeSnapshots: List<Shape>? = null
    private var preTransformBoundingBox: RectF? = null
    private var transformCenterX: Float = 0f
    private var transformCenterY: Float = 0f

    /** Set by [handleBegin] when touch is outside selection; checked/cleared by [handleEnd]. */
    private var pendingCancel = false

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

    fun handleBegin(touchPoint: TouchPoint?): BeginResult {
        if (touchPoint == null) return BeginResult.Ignored
        val notePoint = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)

        if (selectionManager.hasSelection) {
            when {
                selectionManager.isOnRotationHandle(notePoint.x, notePoint.y) -> {
                    startTransform()
                    selectionManager.beginRotate(notePoint)
                    return BeginResult.TransformStarted
                }
                selectionManager.isOnScaleHandle(notePoint.x, notePoint.y).also {
                    if (it != null) { startTransform(); selectionManager.beginScale(it, notePoint) }
                } != null -> return BeginResult.TransformStarted
                selectionManager.isInsideBoundingBox(notePoint.x, notePoint.y) -> {
                    startTransform()
                    selectionManager.beginDrag(notePoint)
                    return BeginResult.TransformStarted
                }
                else -> {
                    val oldBBox = selectionManager.selectionBoundingBox?.let { RectF(it) }
                    pendingCancel = true
                    selectionManager.clearSelection()
                    viewModel.notifySelectionChanged()
                    doPartialRefresh(oldBBox, null, drawOverlay = false)
                    return BeginResult.Cancelled
                }
            }
        } else {
            onLassoStarted()
            selectionManager.beginLasso()
            return BeginResult.LassoStarted
        }
    }

    /**
     * Completes the selection stroke. If the stroke was a cancel (touch outside selection),
     * this method switches back to Draw mode automatically.
     * @return true if the stroke was a cancelled selection (caller should skip further processing)
     */
    fun handleEnd(touchPointList: TouchPointList): Boolean {
        if (pendingCancel) {
            pendingCancel = false
            viewModel.cancelSelection()
            return true
        }

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
        return false
    }

    fun doPartialRefresh(oldBBoxNote: RectF?, newBBoxNote: RectF?, drawOverlay: Boolean = true) {
        val dirtyNote = when {
            oldBBoxNote != null && newBBoxNote != null -> RectF(oldBBoxNote).apply { union(newBBoxNote) }
            oldBBoxNote != null -> RectF(oldBBoxNote)
            newBBoxNote != null -> RectF(newBBoxNote)
            else -> { Log.d("RefreshDebug", "SelectionInputHandler.doPartialRefresh → forceScreenRefresh (no bbox)"); onForceScreenRefresh(); return }
        }
        Log.d("RefreshDebug", "SelectionInputHandler.doPartialRefresh → partialRefresh dirtyRect=$dirtyNote drawOverlay=$drawOverlay")
        bitmapManager.partialRefresh(dirtyNote, shapesManager.shapes(), if (drawOverlay) selectionManager else null, "SelectionInputHandler.doPartialRefresh")
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
