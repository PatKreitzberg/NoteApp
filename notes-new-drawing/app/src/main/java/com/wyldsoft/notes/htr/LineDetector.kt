package com.wyldsoft.notes.htr

import android.graphics.RectF
import com.wyldsoft.notes.domain.models.Shape

data class Line(
    val shapes: List<Shape>,
    val boundingRect: RectF,
    val lineNumber: Int
)

class LineDetector {
    companion object {
        private const val VERTICAL_OVERLAP_THRESHOLD = 0.7f
        private const val MAX_HORIZONTAL_GAP = 500f
        private const val MAX_TEMPORAL_GAP_MS = 10_000L
    }

    fun detectLines(shapes: List<Shape>): List<Line> {
        if (shapes.isEmpty()) return emptyList()

        val shapesWithBounds = shapes.map { shape ->
            shape to computeBoundingRect(shape)
        }.sortedBy { it.second.left }

        val lines = mutableListOf<MutableList<Pair<Shape, RectF>>>()

        for ((shape, rect) in shapesWithBounds) {
            var bestLine: MutableList<Pair<Shape, RectF>>? = null
            var bestOverlap = 0f

            for (line in lines) {
                val lineRect = computeLineBounds(line)
                val overlap = computeVerticalOverlap(rect, lineRect)

                if (overlap > VERTICAL_OVERLAP_THRESHOLD && overlap > bestOverlap) {
                    val lastShapeInLine = line.last()
                    val horizontalGap = rect.left - lastShapeInLine.second.right
                    val temporalGap = shape.timestamp - lastShapeInLine.first.timestamp

                    if (horizontalGap < MAX_HORIZONTAL_GAP || temporalGap < MAX_TEMPORAL_GAP_MS) {
                        bestLine = line
                        bestOverlap = overlap
                    }
                }
            }

            if (bestLine != null) {
                bestLine.add(shape to rect)
            } else {
                lines.add(mutableListOf(shape to rect))
            }
        }

        // Sort lines by vertical position (top of bounding rect)
        val sortedLines = lines.sortedBy { computeLineBounds(it).top }

        return sortedLines.mapIndexed { index, lineShapes ->
            Line(
                shapes = lineShapes.map { it.first },
                boundingRect = computeLineBounds(lineShapes),
                lineNumber = index
            )
        }
    }

    private fun computeBoundingRect(shape: Shape): RectF {
        if (shape.points.isEmpty()) return RectF()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (point in shape.points) {
            if (point.x < minX) minX = point.x
            if (point.y < minY) minY = point.y
            if (point.x > maxX) maxX = point.x
            if (point.y > maxY) maxY = point.y
        }

        return RectF(minX, minY, maxX, maxY)
    }

    private fun computeLineBounds(lineShapes: List<Pair<Shape, RectF>>): RectF {
        if (lineShapes.isEmpty()) return RectF()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for ((_, rect) in lineShapes) {
            if (rect.left < minX) minX = rect.left
            if (rect.top < minY) minY = rect.top
            if (rect.right > maxX) maxX = rect.right
            if (rect.bottom > maxY) maxY = rect.bottom
        }

        return RectF(minX, minY, maxX, maxY)
    }

    private fun computeVerticalOverlap(a: RectF, b: RectF): Float {
        val overlapTop = maxOf(a.top, b.top)
        val overlapBottom = minOf(a.bottom, b.bottom)
        val overlap = maxOf(0f, overlapBottom - overlapTop)

        val aHeight = a.height()
        if (aHeight <= 0) return 0f

        return overlap / aHeight
    }
}
