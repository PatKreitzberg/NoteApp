package com.wyldsoft.notes.utils

import android.graphics.PointF
import android.graphics.RectF
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape

/**
 * Geometric detection utilities for scribble-to-erase and circle-to-select.
 * Extracted from DrawingOperationsHandler to keep it under the 300-line guideline.
 */
object ShapeGeometryUtils {

    private const val SCRIBBLE_COVERAGE_THRESHOLD = 0.80f
    private const val CIRCLE_ENCLOSURE_THRESHOLD = 0.90f

    /**
     * Compute bounding box of a domain Shape from its points.
     */
    fun computeBoundingBox(shape: Shape): RectF? {
        if (shape.points.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (p in shape.points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return RectF(minX, minY, maxX, maxY)
    }

    /**
     * Find SDK shapes on the same layer whose bounding box is >80% covered
     * by the scribble's bounding box.
     */
    fun findShapesCoveredByScribble(
        scribble: Shape,
        shapesManager: ShapesManager
    ): List<BaseShape> {
        val scribbleRect = computeBoundingBox(scribble) ?: return emptyList()
        val activeLayer = scribble.layer
        val result = mutableListOf<BaseShape>()

        for (sdkShape in shapesManager.shapes()) {
            if (sdkShape.id == scribble.id) continue
            if (sdkShape.layer != activeLayer) continue
            val shapeRect = sdkShape.boundingRect ?: continue

            val interLeft = maxOf(scribbleRect.left, shapeRect.left)
            val interTop = maxOf(scribbleRect.top, shapeRect.top)
            val interRight = minOf(scribbleRect.right, shapeRect.right)
            val interBottom = minOf(scribbleRect.bottom, shapeRect.bottom)

            if (interLeft >= interRight || interTop >= interBottom) continue

            val interArea = (interRight - interLeft) * (interBottom - interTop)
            val shapeArea = shapeRect.width() * shapeRect.height()
            if (shapeArea <= 0f) continue

            val coverage = interArea / shapeArea
            if (coverage >= SCRIBBLE_COVERAGE_THRESHOLD) {
                result.add(sdkShape)
            }
        }

        return result
    }

    /**
     * Find SDK shapes on the same layer where >90% of their points
     * fall inside the circle's polygon (the drawn stroke points).
     */
    fun findShapesEncircledBy(
        circle: Shape,
        shapesManager: ShapesManager
    ): List<BaseShape> {
        if (circle.points.size < 3) return emptyList()
        val polygon = circle.points
        val activeLayer = circle.layer
        val result = mutableListOf<BaseShape>()

        for (sdkShape in shapesManager.shapes()) {
            if (sdkShape.id == circle.id) continue
            if (sdkShape.layer != activeLayer) continue
            val touchPoints = sdkShape.touchPointList ?: continue
            if (touchPoints.size() == 0) continue

            var insideCount = 0
            for (i in 0 until touchPoints.size()) {
                val tp = touchPoints.get(i)
                if (pointInPolygon(tp.x, tp.y, polygon)) insideCount++
            }

            val ratio = insideCount.toFloat() / touchPoints.size()
            if (ratio >= CIRCLE_ENCLOSURE_THRESHOLD) {
                result.add(sdkShape)
            }
        }

        return result
    }

    /**
     * Ray-casting point-in-polygon test.
     */
    fun pointInPolygon(x: Float, y: Float, polygon: List<PointF>): Boolean {
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
