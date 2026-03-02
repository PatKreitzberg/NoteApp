package com.wyldsoft.notes.shapemanagement

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape

/**
 * Manages lasso selection and drag-to-move of shapes.
 *
 * Phases:
 * 1. Lasso phase: user draws a freeform lasso; touch points accumulate.
 * 2. Selection phase: point-in-polygon test determines contained shapes; bounding box shown.
 * 3. Move phase: stylus touch inside bounding box drags all selected shapes.
 */
class SelectionManager {

    companion object {
        private const val TAG = "SelectionManager"
    }

    // Lasso state
    private val lassoPoints = mutableListOf<PointF>()
    var isLassoInProgress = false
        private set

    // Selection state
    var selectedShapeIds = setOf<String>()
        private set
    var selectionBoundingBox: RectF? = null
        private set

    // Move state
    var isDragging = false
        private set
    private var dragStartPoint: PointF? = null

    /** True when shapes have been selected (after lasso completes). */
    val hasSelection: Boolean get() = selectedShapeIds.isNotEmpty()

    // --- Lasso Phase ---

    fun beginLasso() {
        lassoPoints.clear()
        isLassoInProgress = true
        Log.d(TAG, "Lasso started")
    }

    /**
     * Feed touch points from a drawing stroke into the lasso polygon.
     * Returns the lasso points for rendering the dashed line.
     */
    fun addLassoPoints(touchPointList: TouchPointList): List<PointF> {
        for (i in 0 until touchPointList.size()) {
            val tp = touchPointList.get(i)
            lassoPoints.add(PointF(tp.x, tp.y))
        }
        return lassoPoints.toList()
    }

    /**
     * Finalize lasso and select shapes whose *any* point is inside the polygon.
     * Returns the set of selected shape IDs.
     */
    fun finishLasso(allShapes: List<BaseShape>): Set<String> {
        isLassoInProgress = false
        if (lassoPoints.size < 3) {
            Log.d(TAG, "Lasso too small (${lassoPoints.size} points), clearing")
            clearSelection()
            return emptySet()
        }

        val selected = mutableSetOf<String>()
        for (shape in allShapes) {
            if (isShapeInsideLasso(shape)) {
                selected.add(shape.id)
            }
        }

        selectedShapeIds = selected
        selectionBoundingBox = calculateBoundingBox(allShapes.filter { it.id in selected })
        lassoPoints.clear()

        Log.d(TAG, "Lasso finished: ${selected.size} shapes selected")
        return selected
    }

    /**
     * A shape is "inside" the lasso if at least one of its points is inside the polygon.
     */
    private fun isShapeInsideLasso(shape: BaseShape): Boolean {
        val points = shape.touchPointList ?: return false
        for (i in 0 until points.size()) {
            val tp = points.get(i)
            if (pointInPolygon(tp.x, tp.y, lassoPoints)) {
                return true
            }
        }
        return false
    }

    /**
     * Ray-casting point-in-polygon test.
     */
    private fun pointInPolygon(x: Float, y: Float, polygon: List<PointF>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y

            val intersect = ((yi > y) != (yj > y)) &&
                    (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private fun calculateBoundingBox(shapes: List<BaseShape>): RectF? {
        if (shapes.isEmpty()) return null
        var rect: RectF? = null
        for (shape in shapes) {
            shape.updateShapeRect()
            val br = shape.boundingRect ?: continue
            if (rect == null) {
                rect = RectF(br)
            } else {
                rect.union(br)
            }
        }
        // Add padding
        rect?.inset(-10f, -10f)
        return rect
    }

    // --- Move Phase ---

    /**
     * Check if a note-coordinate point is inside the current selection bounding box.
     */
    fun isInsideBoundingBox(noteX: Float, noteY: Float): Boolean {
        return selectionBoundingBox?.contains(noteX, noteY) == true
    }

    fun beginDrag(startPoint: PointF) {
        isDragging = true
        dragStartPoint = startPoint
        Log.d(TAG, "Drag started at $startPoint")
    }

    /**
     * Apply move delta to all selected shapes.
     * Returns the delta (dx, dy) applied.
     */
    fun finishDrag(
        endPointList: TouchPointList,
        allShapes: List<BaseShape>
    ): PointF? {
        isDragging = false
        val start = dragStartPoint ?: return null
        if (endPointList.size() == 0) return null

        // Use the last point as the end
        val lastPt = endPointList.get(endPointList.size() - 1)
        val dx = lastPt.x - start.x
        val dy = lastPt.y - start.y
        dragStartPoint = null

        if (Math.abs(dx) < 2f && Math.abs(dy) < 2f) return null

        // Move selected shapes
        for (shape in allShapes) {
            if (shape.id in selectedShapeIds) {
                moveShapeTouchPoints(shape, dx, dy)
            }
        }

        // Update bounding box
        selectionBoundingBox?.offset(dx, dy)

        Log.d(TAG, "Drag finished: dx=$dx, dy=$dy")
        return PointF(dx, dy)
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

    // --- Cancel / Clear ---

    fun clearSelection() {
        lassoPoints.clear()
        isLassoInProgress = false
        selectedShapeIds = emptySet()
        selectionBoundingBox = null
        isDragging = false
        dragStartPoint = null
        Log.d(TAG, "Selection cleared")
    }

    /** Get current lasso points for rendering. */
    fun getLassoPoints(): List<PointF> = lassoPoints.toList()
}
