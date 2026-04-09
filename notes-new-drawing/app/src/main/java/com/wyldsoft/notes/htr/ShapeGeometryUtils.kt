package com.wyldsoft.notes.htr

import android.graphics.RectF
import com.wyldsoft.notes.shapemanagement.shapes.Shape

/**
 * Geometric detection utilities for scribble-to-erase and circle-to-select.
 */
object ShapeGeometryUtils {

    private const val SCRIBBLE_COVERAGE_THRESHOLD = 0.80f
    private const val CIRCLE_ENCLOSURE_THRESHOLD = 0.90f

    /**
     * Compute bounding box of a Shape from its touchPointList.
     */
    fun computeBoundingBox(shape: Shape): RectF? {
        val points = shape.touchPointList?.points ?: return null
        if (points.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (p in points) {
            if (p == null) continue
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return if (minX == Float.MAX_VALUE) null else RectF(minX, minY, maxX, maxY)
    }

    /**
     * Find shapes whose bounding box is >80% covered by the scribble's bounding box.
     * Excludes the scribble shape itself.
     */
    fun findShapesCoveredByScribble(scribble: Shape, shapes: List<Shape>): List<Shape> {
        val scribbleRect = computeBoundingBox(scribble) ?: return emptyList()
        val result = mutableListOf<Shape>()

        for (shape in shapes) {
            if (shape === scribble || shape.entityId == scribble.entityId) continue
            val shapeRect = shape.boundingRect ?: continue

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
                result.add(shape)
            }
        }

        return result
    }

    /**
     * Find shapes where >90% of their touch points fall inside the circle's polygon.
     * Excludes the circle shape itself.
     */
    fun findShapesEncircledBy(circle: Shape, shapes: List<Shape>): List<Shape> {
        val circlePoints = circle.touchPointList?.points ?: return emptyList()
        if (circlePoints.size < 3) return emptyList()
        val polygon = circlePoints.mapNotNull { p -> p?.let { Pair(it.x, it.y) } }
        val result = mutableListOf<Shape>()

        for (shape in shapes) {
            if (shape === circle || shape.entityId == circle.entityId) continue
            val touchPoints = shape.touchPointList?.points ?: continue
            if (touchPoints.isEmpty()) continue

            var insideCount = 0
            for (tp in touchPoints) {
                if (tp == null) continue
                if (pointInPolygon(tp.x, tp.y, polygon)) insideCount++
            }

            val total = touchPoints.filterNotNull().size
            if (total == 0) continue
            val ratio = insideCount.toFloat() / total
            if (ratio >= CIRCLE_ENCLOSURE_THRESHOLD) {
                result.add(shape)
            }
        }

        return result
    }

    /**
     * Ray-casting point-in-polygon test.
     */
    fun pointInPolygon(x: Float, y: Float, polygon: List<Pair<Float, Float>>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].first; val yi = polygon[i].second
            val xj = polygon[j].first; val yj = polygon[j].second
            val intersect = ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }
}
