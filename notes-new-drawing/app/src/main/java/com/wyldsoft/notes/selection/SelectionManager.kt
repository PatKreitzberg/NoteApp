package com.wyldsoft.notes.selection

import android.graphics.RectF
import android.util.Log
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.shapemanagement.shapes.Shape

/**
 * Manages lasso selection logic: point-in-polygon containment testing,
 * bounding rect computation, and shape point translation for move operations.
 *
 * Containment rule: a shape is selected only if ALL of its touch points
 * fall inside the lasso polygon (ray-casting test).
 */
class SelectionManager {
    companion object {
        private const val TAG = "SelectionManager"
    }

    /**
     * Returns shapes from [shapes] where every touch point is inside the lasso polygon.
     * [lassoPoints] must be in the same coordinate space as the shapes (note-space).
     */
    fun findShapesInsideLasso(shapes: List<Shape>, lassoPoints: TouchPointList): List<Shape> {
        Log.d(TAG, "findShapesInsideLasso shapes=${shapes.size} lassoSize=${lassoPoints.size()}")
        val poly = lassoPoints.points.filterNotNull().map { Pair(it.x, it.y) }
        if (poly.size < 3) return emptyList()
        return shapes.filter { isShapeFullyInsideLasso(it, poly) }
    }

    /**
     * Returns the union bounding rect of all provided shapes, or null if the list is empty.
     */
    fun computeBoundingRect(shapes: List<Shape>): RectF? {
        Log.d(TAG, "computeBoundingRect shapes=${shapes.size}")
        if (shapes.isEmpty()) return null
        val result = RectF()
        var first = true
        for (shape in shapes) {
            val br = shape.boundingRect ?: continue
            if (first) {
                result.set(br)
                first = false
            } else {
                result.union(br)
            }
        }
        return if (first) null else result
    }

    /**
     * Translates all touch points in [shape] by ([deltaNoteX], [deltaNoteY]) in note-space.
     * Rebuilds the TouchPointList and recomputes bounding rects.
     */
    fun translateShape(shape: Shape, deltaNoteX: Float, deltaNoteY: Float) {
        Log.d(TAG, "translateShape dx=$deltaNoteX dy=$deltaNoteY")
        val oldList = shape.touchPointList ?: return
        val newList = TouchPointList()
        for (pt in oldList.points) {
            if (pt == null) continue
            val newPt = TouchPoint()
            newPt.x = pt.x + deltaNoteX
            newPt.y = pt.y + deltaNoteY
            newPt.pressure = pt.pressure
            newPt.tiltX = pt.tiltX
            newPt.tiltY = pt.tiltY
            newPt.timestamp = pt.timestamp
            newList.add(newPt)
        }
        shape.touchPointList = newList
        shape.originRect = null
        shape.boundingRect = null
        shape.updateShapeRect()
    }

    private fun isShapeFullyInsideLasso(shape: Shape, poly: List<Pair<Float, Float>>): Boolean {
        val points = shape.touchPointList?.points ?: return false
        return points.filterNotNull().all { pt -> isPointInPolygon(pt.x, pt.y, poly) }
    }

    /**
     * Ray-casting point-in-polygon algorithm.
     * Returns true if (px, py) is strictly inside the polygon defined by [poly].
     */
    private fun isPointInPolygon(px: Float, py: Float, poly: List<Pair<Float, Float>>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val xi = poly[i].first; val yi = poly[i].second
            val xj = poly[j].first; val yj = poly[j].second
            val intersect = ((yi > py) != (yj > py)) &&
                    (px < (xj - xi) * (py - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }
}
