package com.wyldsoft.notes.selection

import android.graphics.RectF
import android.util.Log
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.shapemanagement.shapes.Shape
import com.wyldsoft.notes.utils.copyWith
import kotlin.math.cos
import kotlin.math.sin

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
     * [activeLayer] filters shapes to only the active layer; -1 means all layers.
     */
    fun findShapesInsideLasso(
        shapes: List<Shape>,
        lassoPoints: TouchPointList,
        activeLayer: Int = -1
    ): List<Shape> {
        Log.d(TAG, "findShapesInsideLasso shapes=${shapes.size} lassoSize=${lassoPoints.size()} activeLayer=$activeLayer")
        val candidateShapes = if (activeLayer == -1) shapes else shapes.filter { it.layer == activeLayer }
        val poly = lassoPoints.points.filterNotNull().map { Pair(it.x, it.y) }
        if (poly.size < 3) return emptyList()
        return candidateShapes.filter { isShapeFullyInsideLasso(it, poly) }
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
     * Applies [transform] to every (x, y) in [shape]'s TouchPointList, rebuilds the list,
     * and recomputes the bounding rect. Shared by translate, scale, and rotate.
     */
    private fun transformShapePoints(shape: Shape, transform: (Float, Float) -> Pair<Float, Float>) {
        val oldList = shape.touchPointList ?: return
        val newList = TouchPointList()
        for (pt in oldList.points) {
            if (pt == null) continue
            val (newX, newY) = transform(pt.x, pt.y)
            newList.add(pt.copyWith(newX, newY))
        }
        shape.touchPointList = newList
        shape.boundingRect = null
        shape.originRect = null
        shape.updateShapeRect()
    }

    /**
     * Translates all touch points in [shape] by ([deltaNoteX], [deltaNoteY]) in note-space.
     */
    fun translateShape(shape: Shape, deltaNoteX: Float, deltaNoteY: Float) {
        Log.d(TAG, "translateShape dx=$deltaNoteX dy=$deltaNoteY")
        transformShapePoints(shape) { x, y -> Pair(x + deltaNoteX, y + deltaNoteY) }
    }

    /**
     * Scales all touch points in each shape around ([anchorNoteX], [anchorNoteY]) in note-space.
     * [scaleX] and [scaleY] are multipliers; values < 1 shrink, > 1 grow.
     */
    fun scaleShapes(
        shapes: List<Shape>,
        anchorNoteX: Float,
        anchorNoteY: Float,
        scaleX: Float,
        scaleY: Float
    ) {
        Log.d(TAG, "scaleShapes shapes=${shapes.size} scaleX=$scaleX scaleY=$scaleY")
        for (shape in shapes) {
            transformShapePoints(shape) { x, y ->
                Pair(
                    anchorNoteX + (x - anchorNoteX) * scaleX,
                    anchorNoteY + (y - anchorNoteY) * scaleY
                )
            }
        }
    }

    /**
     * Rotates all touch points in each shape by [angleRad] radians around
     * ([centerNoteX], [centerNoteY]) in note-space.
     */
    fun rotateShapes(
        shapes: List<Shape>,
        centerNoteX: Float,
        centerNoteY: Float,
        angleRad: Float
    ) {
        Log.d(TAG, "rotateShapes shapes=${shapes.size} angleRad=$angleRad")
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        for (shape in shapes) {
            transformShapePoints(shape) { x, y ->
                val dx = x - centerNoteX; val dy = y - centerNoteY
                Pair(centerNoteX + dx * cosA - dy * sinA, centerNoteY + dx * sinA + dy * cosA)
            }
        }
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
