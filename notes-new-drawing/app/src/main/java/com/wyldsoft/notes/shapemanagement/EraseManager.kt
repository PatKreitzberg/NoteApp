package com.wyldsoft.notes.shapemanagement

import android.graphics.RectF
import android.util.Log
import com.wyldsoft.notes.utils.calculateShapesBoundingBox
import android.view.SurfaceView
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.refreshingscreen.PartialEraseRefresh
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.rendering.RendererHelper
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape

class EraseManager(
    private val surfaceView: SurfaceView,
    private val rxManager: RxManager?,
    private val bitmapManager: BitmapManager,
    private val onShapeRemoved: (String) -> Unit
) {
    /** Secondary constructor for non-Onyx devices (no RxManager needed) */
    constructor(
        surfaceView: SurfaceView,
        bitmapManager: BitmapManager,
        onShapeRemoved: (String) -> Unit
    ) : this(surfaceView, null, bitmapManager, onShapeRemoved)

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

            // Perform partial refresh of the erased area (only on Onyx with RxManager)
            if (rxManager != null) {
                refreshRect?.let { rect: RectF ->
                    partialEraseRefresh.performPartialRefresh(
                        surfaceView,
                        rect,
                        shapesManager.shapes(),
                        rendererHelper,
                        rxManager
                    )
                }
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
        return calculateShapesBoundingBox(erasedShapes, padding = 20f)
    }
}