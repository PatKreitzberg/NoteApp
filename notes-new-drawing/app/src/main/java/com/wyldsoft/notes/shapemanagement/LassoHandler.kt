package com.wyldsoft.notes.shapemanagement

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.utils.calculateShapesBoundingBox
import com.wyldsoft.notes.utils.touchPointListToPoints

/**
 * Manages the lasso-draw → select phase.
 * Extracted from SelectionManager to keep it under 300 lines.
 */
class LassoHandler {
    companion object {
        private const val TAG = "LassoHandler"
    }

    private val lassoPoints = mutableListOf<PointF>()
    var isInProgress = false
        private set

    fun begin() {
        lassoPoints.clear()
        isInProgress = true
        Log.d(TAG, "Lasso started")
    }

    fun addPoints(touchPointList: TouchPointList): List<PointF> {
        lassoPoints.addAll(touchPointListToPoints(touchPointList))
        return lassoPoints.toList()
    }

    /**
     * Finalize lasso and return (selectedIds, boundingBox).
     * BoundingBox is null if nothing was selected.
     */
    /**
     * Finalize lasso and return (selectedIds, boundingBox).
     * BoundingBox is null if nothing was selected.
     * @param activeLayer if > 0, only select shapes on that layer
     */
    fun finish(allShapes: List<BaseShape>, activeLayer: Int = -1): Pair<Set<String>, RectF?> {
        isInProgress = false
        if (lassoPoints.size < 3) {
            Log.d(TAG, "Lasso too small (${lassoPoints.size} points)")
            lassoPoints.clear()
            return Pair(emptySet(), null)
        }

        val candidateShapes = if (activeLayer > 0) {
            allShapes.filter { it.layer == activeLayer }
        } else {
            allShapes
        }

        val selected = mutableSetOf<String>()
        for (shape in candidateShapes) {
            if (isShapeInside(shape)) selected.add(shape.id)
        }

        val boundingBox = if (selected.isNotEmpty()) {
            val selectedShapes = allShapes.filter { it.id in selected }
            selectedShapes.forEach { it.updateShapeRect() }
            calculateShapesBoundingBox(selectedShapes)
        } else null

        lassoPoints.clear()
        Log.d(TAG, "Lasso finished: ${selected.size} shapes selected")
        return Pair(selected, boundingBox)
    }

    fun getPoints(): List<PointF> = lassoPoints.toList()

    fun clear() {
        lassoPoints.clear()
        isInProgress = false
    }

    private fun isShapeInside(shape: BaseShape): Boolean {
        val points = shape.touchPointList ?: return false
        if (points.size() == 0) return false
        for (i in 0 until points.size()) {
            val tp = points.get(i)
            if (!pointInPolygon(tp.x, tp.y, lassoPoints)) return false
        }
        return true
    }

    private fun pointInPolygon(x: Float, y: Float, polygon: List<PointF>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x; val yi = polygon[i].y
            val xj = polygon[j].x; val yj = polygon[j].y
            val intersect = ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }
}
