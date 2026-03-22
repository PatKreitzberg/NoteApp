package com.wyldsoft.notes.shapemanagement

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.utils.calculateShapesBoundingBox
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Manages the scale transform phase of selection.
 * Extracted from SelectionManager to keep it under 300 lines.
 */
class SelectionScaleHandler {
    companion object {
        private const val TAG = "SelectionScaleHandler"
    }

    private var anchorCornerIndex: Int = -1
    private var startDistance: Float = 0f
    private var lastDistance: Float = 0f

    fun begin(cornerIndex: Int, startPoint: PointF, boundingBox: RectF) {
        anchorCornerIndex = cornerIndex
        val centerX = boundingBox.centerX()
        val centerY = boundingBox.centerY()
        startDistance = hypot(startPoint.x - centerX, startPoint.y - centerY)
        lastDistance = startDistance
        Log.d(TAG, "Scale started from corner $cornerIndex")
    }

    /**
     * Incrementally scale shapes. Returns new bounding box, or null if no update needed.
     */
    fun update(
        currentPoint: PointF,
        allShapes: List<BaseShape>,
        selectedIds: Set<String>,
        boundingBox: RectF
    ): RectF? {
        if (lastDistance < 1f) return null
        val centerX = boundingBox.centerX()
        val centerY = boundingBox.centerY()
        val currentDistance = hypot(currentPoint.x - centerX, currentPoint.y - centerY)
        val factor = currentDistance / lastDistance
        if (abs(factor - 1f) < 0.005f) return null

        for (shape in allShapes) {
            if (shape.id in selectedIds) scaleShapePoints(shape, factor, centerX, centerY)
        }
        lastDistance = currentDistance
        val selected = allShapes.filter { it.id in selectedIds }
        selected.forEach { it.updateShapeRect() }
        return calculateShapesBoundingBox(selected)
    }

    /**
     * Finish scale. Returns (totalFactor, newBoundingBox). Factor is null if negligible.
     */
    fun finish(
        endPointList: TouchPointList,
        allShapes: List<BaseShape>,
        selectedIds: Set<String>,
        boundingBox: RectF
    ): Pair<Float?, RectF?> {
        if (startDistance < 1f) return Pair(null, null)

        if (endPointList.size() == 0) {
            val totalFactor = if (lastDistance > 0f) lastDistance / startDistance else 1f
            return Pair(if (abs(totalFactor - 1f) < 0.01f) null else totalFactor, null)
        }

        val lastPt = endPointList.get(endPointList.size() - 1)
        val centerX = boundingBox.centerX()
        val centerY = boundingBox.centerY()
        val endDistance = hypot(lastPt.x - centerX, lastPt.y - centerY)

        if (lastDistance > 0f) {
            val remainingFactor = endDistance / lastDistance
            if (abs(remainingFactor - 1f) > 0.005f) {
                for (shape in allShapes) {
                    if (shape.id in selectedIds) scaleShapePoints(shape, remainingFactor, centerX, centerY)
                }
            }
        }

        val selected = allShapes.filter { it.id in selectedIds }
        selected.forEach { it.updateShapeRect() }
        val newBox = calculateShapesBoundingBox(selected)

        val totalFactor = endDistance / startDistance
        Log.d(TAG, "Scale finished: factor=$totalFactor")
        return Pair(if (abs(totalFactor - 1f) < 0.01f) null else totalFactor, newBox)
    }

    fun reset() {
        anchorCornerIndex = -1
        startDistance = 0f
        lastDistance = 0f
    }

    private fun scaleShapePoints(shape: BaseShape, scaleFactor: Float, centerX: Float, centerY: Float) {
        val oldList = shape.touchPointList ?: return
        val newList = TouchPointList()
        for (i in 0 until oldList.size()) {
            val tp = oldList.get(i)
            newList.add(TouchPoint(
                centerX + (tp.x - centerX) * scaleFactor,
                centerY + (tp.y - centerY) * scaleFactor,
                tp.pressure, tp.size, tp.tiltX, tp.tiltY, tp.timestamp
            ))
        }
        shape.touchPointList = newList
        shape.updateShapeRect()
    }
}
