package com.wyldsoft.notes.shapemanagement

import android.graphics.RectF
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape

class EraseManager {
    companion object {
        private const val ERASE_RADIUS = 15f // Default erase radius in pixels
    }

    fun findIntersectingShapes(
        touchPointList: TouchPointList,
        shapes: List<BaseShape>,
        eraseRadius: Float = ERASE_RADIUS
    ): List<BaseShape> {
        val intersectingShapes = mutableListOf<BaseShape>()

        for (shape in shapes) {
            if (shape.hitTestPoints(touchPointList, eraseRadius)) {
                intersectingShapes.add(shape)
            }
        }

        return intersectingShapes
    }

    fun calculateRefreshRect(erasedShapes: List<BaseShape>): RectF? {
        if (erasedShapes.isEmpty()) return null

        var refreshRect: RectF? = null
        
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