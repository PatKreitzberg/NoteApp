package com.wyldsoft.notes.shapemanagement

import android.graphics.RectF
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.shapemanagement.shapes.Shape

class EraseManager {
    companion object {
        private const val ERASE_RADIUS = 15f // Default erase radius in pixels
    }

    fun findIntersectingShapes(
        touchPointList: TouchPointList,
        drawnShapes: List<Shape>,
        eraseRadius: Float = ERASE_RADIUS
    ): List<Shape> {
        val intersectingShapes = mutableListOf<Shape>()

        for (shape in drawnShapes) {
            if (shape.hitTestPoints(touchPointList, eraseRadius)) {
                intersectingShapes.add(shape)
            }
        }

        return intersectingShapes
    }

    fun calculateRefreshRect(erasedShapes: List<Shape>): RectF? {
        if (erasedShapes.isEmpty()) return null

        var refreshRect: RectF? = null // fixme start with non-null then can avoid if (refreshRect == null) {
        
        for (shape in erasedShapes) {
            val boundingRect = shape.boundingRect
            if (boundingRect != null) {
                if (refreshRect == null) {
                    refreshRect = RectF(boundingRect)
                } else {
                    refreshRect.union(boundingRect)
                }
            }
        }

        // Add some padding around the refresh area
        refreshRect?.let { rect ->
            val padding = 20f
            rect.inset(-padding, -padding)
        }

        return refreshRect
    }
}