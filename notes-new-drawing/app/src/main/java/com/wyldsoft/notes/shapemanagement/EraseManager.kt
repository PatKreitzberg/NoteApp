package com.wyldsoft.notes.shapemanagement

import android.graphics.RectF
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.refreshingscreen.PartialEraseRefresh
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.rendering.RendererHelper
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape

class EraseManager(
    private val surfaceView: SurfaceView,
    private val rxManager: RxManager,
    private val bitmapManager: BitmapManager,
    private val onShapeRemoved: (String) -> Unit
) {
    private val partialEraseRefresh = PartialEraseRefresh()
    private val rendererHelper = RendererHelper()

    companion object {
        private const val ERASE_RADIUS = 15f // Default erase radius in pixels
        private const val TAG = "EraseManager"
    }

    internal fun handleErasing(noteErasePointList: TouchPointList, shapesManager: ShapesManager) {
        // Find shapes that intersect with the erase touch points
        val intersectingShapes = findIntersectingShapes(
            noteErasePointList,
            shapesManager.shapes()
        )

        if (intersectingShapes.isNotEmpty()) {
            Log.d(TAG, "Found ${intersectingShapes.size} shapes to erase")

            // Calculate refresh area before removing shapes
            val refreshRect = calculateRefreshRect(intersectingShapes)

            // Remove intersecting shapes from our shape list
            shapesManager.removeAll(intersectingShapes.toSet())
            for (shape in intersectingShapes) {
                onShapeRemoved(shape.id)
            }

            // Perform partial refresh of the erased area
            refreshRect?.let { rect: RectF ->
                partialEraseRefresh.performPartialRefresh(
                    surfaceView,
                    rect,
                    shapesManager.shapes(), // Pass remaining shapes
                    rendererHelper,
                    rxManager
                )
            }

            // Also update the main bitmap by recreating it from remaining shapes
            bitmapManager.recreateBitmapFromShapes(shapesManager.shapes())
        }
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