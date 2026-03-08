package com.wyldsoft.notes.shapemanagement

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.utils.calculateShapesBoundingBox
import kotlin.math.hypot

enum class TransformMode {
    NONE, MOVE, SCALE, ROTATE
}

data class HandlePositions(
    val corners: List<PointF>,
    val rotationHandle: PointF
)

/**
 * Coordinates lasso selection and drag/scale/rotate transforms.
 * Delegates lasso logic to [LassoHandler], scale to [SelectionScaleHandler],
 * and rotate to [SelectionRotateHandler].
 */
class SelectionManager {

    companion object {
        private const val TAG = "SelectionManager"
        private const val HANDLE_HIT_RADIUS = 30f
        private const val ROTATION_HANDLE_OFFSET = 40f
    }

    private val lassoHandler = LassoHandler()
    private val scaleHandler = SelectionScaleHandler()
    private val rotateHandler = SelectionRotateHandler()

    val isLassoInProgress: Boolean get() = lassoHandler.isInProgress

    var selectedShapeIds = setOf<String>()
        private set
    var selectionBoundingBox: RectF? = null
        private set

    var isDragging = false
        private set
    private var dragStartPoint: PointF? = null
    private var lastDragPoint: PointF? = null
    private var accumulatedDragDx: Float = 0f
    private var accumulatedDragDy: Float = 0f

    var transformMode = TransformMode.NONE
        private set

    val hasSelection: Boolean get() = selectedShapeIds.isNotEmpty()

    // --- Lasso Phase (delegated) ---

    fun beginLasso() = lassoHandler.begin()

    fun addLassoPoints(touchPointList: TouchPointList): List<PointF> =
        lassoHandler.addPoints(touchPointList)

    fun finishLasso(allShapes: List<BaseShape>): Set<String> {
        val (ids, box) = lassoHandler.finish(allShapes)
        selectedShapeIds = ids
        selectionBoundingBox = box
        return ids
    }

    // --- Handle Detection ---

    fun isInsideBoundingBox(noteX: Float, noteY: Float): Boolean =
        selectionBoundingBox?.contains(noteX, noteY) == true

    fun getHandlePositions(): HandlePositions? {
        val box = selectionBoundingBox ?: return null
        val corners = listOf(
            PointF(box.left, box.top),
            PointF(box.right, box.top),
            PointF(box.right, box.bottom),
            PointF(box.left, box.bottom)
        )
        return HandlePositions(corners, PointF((box.left + box.right) / 2f, box.top - ROTATION_HANDLE_OFFSET))
    }

    fun isOnScaleHandle(noteX: Float, noteY: Float): Int? {
        val handles = getHandlePositions() ?: return null
        for ((i, corner) in handles.corners.withIndex()) {
            if (hypot(noteX - corner.x, noteY - corner.y) <= HANDLE_HIT_RADIUS) return i
        }
        return null
    }

    fun isOnRotationHandle(noteX: Float, noteY: Float): Boolean {
        val handles = getHandlePositions() ?: return false
        return hypot(noteX - handles.rotationHandle.x, noteY - handles.rotationHandle.y) <= HANDLE_HIT_RADIUS
    }

    // --- Move Phase ---

    fun beginDrag(startPoint: PointF) {
        isDragging = true
        dragStartPoint = startPoint
        lastDragPoint = startPoint
        accumulatedDragDx = 0f
        accumulatedDragDy = 0f
    }

    fun updateDrag(currentPoint: PointF, allShapes: List<BaseShape>) {
        val last = lastDragPoint ?: return
        val dx = currentPoint.x - last.x
        val dy = currentPoint.y - last.y
        if (Math.abs(dx) < 1f && Math.abs(dy) < 1f) return

        for (shape in allShapes) {
            if (shape.id in selectedShapeIds) moveShapeTouchPoints(shape, dx, dy)
        }
        selectionBoundingBox?.offset(dx, dy)
        lastDragPoint = currentPoint
        accumulatedDragDx += dx
        accumulatedDragDy += dy
    }

    fun finishDrag(endPointList: TouchPointList, allShapes: List<BaseShape>): PointF? {
        isDragging = false
        val start = dragStartPoint ?: return null
        if (endPointList.size() == 0) {
            dragStartPoint = null; lastDragPoint = null
            val totalDx = accumulatedDragDx; val totalDy = accumulatedDragDy
            accumulatedDragDx = 0f; accumulatedDragDy = 0f
            return if (Math.abs(totalDx) < 2f && Math.abs(totalDy) < 2f) null else PointF(totalDx, totalDy)
        }

        val lastPt = endPointList.get(endPointList.size() - 1)
        val totalDx = lastPt.x - start.x
        val totalDy = lastPt.y - start.y
        dragStartPoint = null; lastDragPoint = null

        if (Math.abs(totalDx) < 2f && Math.abs(totalDy) < 2f) {
            accumulatedDragDx = 0f; accumulatedDragDy = 0f
            return null
        }

        val remainingDx = totalDx - accumulatedDragDx
        val remainingDy = totalDy - accumulatedDragDy
        if (Math.abs(remainingDx) > 0.5f || Math.abs(remainingDy) > 0.5f) {
            for (shape in allShapes) {
                if (shape.id in selectedShapeIds) moveShapeTouchPoints(shape, remainingDx, remainingDy)
            }
            selectionBoundingBox?.offset(remainingDx, remainingDy)
        }

        accumulatedDragDx = 0f; accumulatedDragDy = 0f
        Log.d(TAG, "Drag finished: dx=$totalDx, dy=$totalDy")
        return PointF(totalDx, totalDy)
    }

    private fun moveShapeTouchPoints(shape: BaseShape, dx: Float, dy: Float) {
        val oldList = shape.touchPointList ?: return
        val newList = TouchPointList()
        for (i in 0 until oldList.size()) {
            val tp = oldList.get(i)
            newList.add(TouchPoint(tp.x + dx, tp.y + dy, tp.pressure, tp.size, tp.timestamp))
        }
        shape.touchPointList = newList
        shape.updateShapeRect()
    }

    // --- Scale Phase (delegated) ---

    fun beginScale(cornerIndex: Int, startPoint: PointF) {
        transformMode = TransformMode.SCALE
        scaleHandler.begin(cornerIndex, startPoint, selectionBoundingBox ?: return)
    }

    fun updateScale(currentPoint: PointF, allShapes: List<BaseShape>) {
        val box = selectionBoundingBox ?: return
        scaleHandler.update(currentPoint, allShapes, selectedShapeIds, box)?.let { selectionBoundingBox = it }
    }

    fun finishScale(endPointList: TouchPointList, allShapes: List<BaseShape>): Float? {
        transformMode = TransformMode.NONE
        val box = selectionBoundingBox ?: return null
        val (factor, newBox) = scaleHandler.finish(endPointList, allShapes, selectedShapeIds, box)
        newBox?.let { selectionBoundingBox = it }
        scaleHandler.reset()
        return factor
    }

    // --- Rotate Phase (delegated) ---

    fun beginRotate(startPoint: PointF) {
        transformMode = TransformMode.ROTATE
        rotateHandler.begin(startPoint, selectionBoundingBox ?: return)
    }

    fun updateRotate(currentPoint: PointF, allShapes: List<BaseShape>) {
        val box = selectionBoundingBox ?: return
        rotateHandler.update(currentPoint, allShapes, selectedShapeIds, box)?.let { selectionBoundingBox = it }
    }

    fun finishRotate(endPointList: TouchPointList, allShapes: List<BaseShape>): Float? {
        transformMode = TransformMode.NONE
        val box = selectionBoundingBox ?: return null
        val (angle, newBox) = rotateHandler.finish(endPointList, allShapes, selectedShapeIds, box)
        newBox?.let { selectionBoundingBox = it }
        rotateHandler.reset()
        return angle
    }

    // --- Clear ---

    fun clearSelection() {
        lassoHandler.clear()
        selectedShapeIds = emptySet()
        selectionBoundingBox = null
        isDragging = false
        dragStartPoint = null; lastDragPoint = null
        accumulatedDragDx = 0f; accumulatedDragDy = 0f
        transformMode = TransformMode.NONE
        scaleHandler.reset()
        rotateHandler.reset()
        Log.d(TAG, "Selection cleared")
    }

    fun getLassoPoints(): List<PointF> = lassoHandler.getPoints()

    fun setSelection(ids: Set<String>, boundingBox: RectF) {
        selectedShapeIds = ids
        selectionBoundingBox = RectF(boundingBox)
    }
}
