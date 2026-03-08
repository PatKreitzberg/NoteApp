package com.wyldsoft.notes.shapemanagement

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.utils.calculateShapesBoundingBox
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manages the rotate transform phase of selection.
 * Extracted from SelectionManager to keep it under 300 lines.
 */
class SelectionRotateHandler {
    companion object {
        private const val TAG = "SelectionRotateHandler"
    }

    private var startAngle: Float = 0f
    private var lastAngle: Float = 0f
    private var rotationCenterX: Float = 0f
    private var rotationCenterY: Float = 0f

    fun begin(startPoint: PointF, boundingBox: RectF) {
        rotationCenterX = boundingBox.centerX()
        rotationCenterY = boundingBox.centerY()
        startAngle = atan2(startPoint.y - rotationCenterY, startPoint.x - rotationCenterX)
        lastAngle = startAngle
        Log.d(TAG, "Rotate started")
    }

    /**
     * Incrementally rotate shapes around the fixed center captured at begin().
     * Returns new bounding box, or null if no update needed.
     */
    fun update(
        currentPoint: PointF,
        allShapes: List<BaseShape>,
        selectedIds: Set<String>,
        boundingBox: RectF
    ): RectF? {
        val centerX = rotationCenterX
        val centerY = rotationCenterY
        val currentAngle = atan2(currentPoint.y - centerY, currentPoint.x - centerX)
        val incrementalAngle = currentAngle - lastAngle
        if (abs(incrementalAngle) < 0.005f) return null

        for (shape in allShapes) {
            if (shape.id in selectedIds) rotateShapePoints(shape, incrementalAngle, centerX, centerY)
        }
        lastAngle = currentAngle
        val selected = allShapes.filter { it.id in selectedIds }
        selected.forEach { it.updateShapeRect() }
        return calculateShapesBoundingBox(selected)
    }

    /**
     * Finish rotation. Returns (totalAngleRad, newBoundingBox). Angle is null if negligible.
     */
    fun finish(
        endPointList: TouchPointList,
        allShapes: List<BaseShape>,
        selectedIds: Set<String>,
        boundingBox: RectF
    ): Pair<Float?, RectF?> {
        if (endPointList.size() == 0) {
            val totalAngle = lastAngle - startAngle
            return Pair(if (abs(totalAngle) < 0.01f) null else totalAngle, null)
        }

        val lastPt = endPointList.get(endPointList.size() - 1)
        val centerX = rotationCenterX
        val centerY = rotationCenterY
        val endAngle = atan2(lastPt.y - centerY, lastPt.x - centerX)

        val remainingAngle = endAngle - lastAngle
        if (abs(remainingAngle) > 0.005f) {
            for (shape in allShapes) {
                if (shape.id in selectedIds) rotateShapePoints(shape, remainingAngle, centerX, centerY)
            }
        }

        val selected = allShapes.filter { it.id in selectedIds }
        selected.forEach { it.updateShapeRect() }
        val newBox = calculateShapesBoundingBox(selected)

        val totalAngle = endAngle - startAngle
        Log.d(TAG, "Rotate finished: ${Math.toDegrees(totalAngle.toDouble())} deg")
        return Pair(if (abs(totalAngle) < 0.01f) null else totalAngle, newBox)
    }

    fun reset() {
        startAngle = 0f
        lastAngle = 0f
        rotationCenterX = 0f
        rotationCenterY = 0f
    }

    private fun rotateShapePoints(shape: BaseShape, angleRad: Float, centerX: Float, centerY: Float) {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        val oldList = shape.touchPointList ?: return
        val newList = TouchPointList()
        for (i in 0 until oldList.size()) {
            val tp = oldList.get(i)
            val dx = tp.x - centerX
            val dy = tp.y - centerY
            newList.add(TouchPoint(
                centerX + dx * cosA - dy * sinA,
                centerY + dx * sinA + dy * cosA,
                tp.pressure, tp.size, tp.timestamp
            ))
        }
        shape.touchPointList = newList
        shape.updateShapeRect()
    }
}
