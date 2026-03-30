package com.wyldsoft.notes.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.refreshingscreen.PartialEraseRefresh
import com.wyldsoft.notes.shapemanagement.EraseManager
import com.wyldsoft.notes.shapemanagement.ShapeFactory
import com.wyldsoft.notes.shapemanagement.shapes.Shape
import androidx.core.graphics.createBitmap

/**
 * Manages the drawing pipeline: shape creation, rendering to bitmap, and erasing.
 * Owns the in-memory list of drawn shapes and coordinates viewport transforms
 * for rendering.
 *
 * Extracted from OnyxDrawingActivity to keep that class focused on SDK lifecycle.
 */
class DrawingPipeline(
    private val viewportManager: ViewportManager
) {
    private val TAG = "DrawingPipeline"

    private val drawnShapes = mutableListOf<Shape>()
    private val eraseManager = EraseManager()
    private val partialEraseRefresh = PartialEraseRefresh()

    fun clearShapes() {
        Log.d(TAG, "clearShapes")
        drawnShapes.clear()
    }

    fun drawScribbleToBitmap(
        points: List<TouchPoint>,
        touchPointList: TouchPointList,
        bitmap: Bitmap,
        penProfile: PenProfile
    ) {
        Log.d(TAG, "drawScribbleToBitmap list size ${touchPointList.size()}")
        val notePointList = viewportManager.viewportToNoteTouchPoints(touchPointList)
        val shape = createShapeFromPenType(notePointList, penProfile)
        drawnShapes.add(shape)
        renderShapeToBitmap(shape, bitmap)
    }

    fun handleErasing(
        erasePointList: TouchPointList,
        bitmap: Bitmap?,
        bitmapCanvas: Canvas?,
        surfaceView: SurfaceView,
        rxManager: RxManager,
        bitmapProvider: () -> BitmapState
    ): BitmapState? {
        Log.d(TAG, "handleErasing called")

        val noteErasePointList = viewportManager.viewportToNoteTouchPoints(erasePointList)
        val intersectingShapes = eraseManager.findIntersectingShapes(
            noteErasePointList,
            drawnShapes
        )
        Log.d(TAG, "handleErasing done checking intersections")

        if (intersectingShapes.isNotEmpty()) {
            Log.d(TAG, "handleErasing found ${intersectingShapes.size} shapes to erase")
            drawnShapes.removeAll(intersectingShapes.toSet())

            val newState = recreateBitmapFromShapes(
                bitmap, surfaceView.width, surfaceView.height
            )

            val refreshRect = eraseManager.calculateRefreshRect(intersectingShapes)
            if (refreshRect != null) {
                val viewportRect = viewportManager.noteToViewport(refreshRect)
                partialEraseRefresh.performPartialRefresh(
                    surfaceView,
                    viewportRect,
                    drawnShapes.toList(),
                    viewportManager,
                    rxManager
                )
            }
            Log.d(TAG, "erase partial refresh enqueued to RxManager")
            return newState
        }
        Log.d(TAG, "handleErasing done — no shapes erased")
        return null
    }

    /**
     * Recreates the full offscreen bitmap by re-rendering all stored shapes.
     * Returns the new bitmap and canvas so the caller can update its fields.
     */
    fun recreateBitmapFromShapes(
        currentBitmap: Bitmap?,
        width: Int,
        height: Int
    ): BitmapState {
        Log.d(TAG, "recreateBitmapFromShapes scale=${viewportManager.scale} scrollX=${viewportManager.scrollX} scrollY=${viewportManager.scrollY}")

        val bmp: Bitmap
        val canvas: Canvas
        if (currentBitmap != null && !currentBitmap.isRecycled
            && currentBitmap.width == width && currentBitmap.height == height
        ) {
            Log.d(TAG, "reusing existing bitmap")
            bmp = currentBitmap
            canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
        } else {
            Log.d(TAG, "creating new bitmap (old size mismatch or null)")
            bmp = createBitmap(width, height)
            canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
        }

        val renderContext = RenderContext.createForBitmap(bmp, canvas)

        Log.d(TAG, "Drawing ${drawnShapes.size} shapes")
        for (shape in drawnShapes) {
            shape.renderInViewport(renderContext, viewportManager)
        }

        return BitmapState(bmp, canvas)
    }

    private fun createShapeFromPenType(
        touchPointList: TouchPointList,
        penProfile: PenProfile
    ): Shape {
        Log.d(TAG, "createShapeFromPenType")
        val shapeType = when (penProfile.penType) {
            PenType.BALLPEN, PenType.PENCIL -> ShapeFactory.SHAPE_PENCIL_SCRIBBLE
            PenType.FOUNTAIN -> ShapeFactory.SHAPE_BRUSH_SCRIBBLE
            PenType.MARKER -> ShapeFactory.SHAPE_MARKER_SCRIBBLE
            PenType.CHARCOAL, PenType.CHARCOAL_V2 -> ShapeFactory.SHAPE_CHARCOAL_SCRIBBLE
            PenType.NEO_BRUSH -> ShapeFactory.SHAPE_NEO_BRUSH_SCRIBBLE
            PenType.DASH -> ShapeFactory.SHAPE_DASH_SCRIBBLE
        }

        val shape = ShapeFactory.createShape(shapeType)
        shape.touchPointList = touchPointList
        shape.strokeColor = penProfile.getColorAsInt()
        shape.strokeWidth = penProfile.strokeWidth
        shape.shapeType = shapeType
        shape.updateShapeRect()

        if (penProfile.penType == PenType.CHARCOAL_V2) {
            shape.texture = com.onyx.android.sdk.data.note.PenTexture.CHARCOAL_SHAPE_V2
        } else if (penProfile.penType == PenType.CHARCOAL) {
            shape.texture = com.onyx.android.sdk.data.note.PenTexture.CHARCOAL_SHAPE_V1
        }

        return shape
    }

    private fun renderShapeToBitmap(shape: Shape, bitmap: Bitmap) {
        Log.d(TAG, "renderShapeToBitmap")
        val renderContext = RenderContext.createForBitmap(bitmap, Canvas(bitmap))
        shape.renderInViewport(renderContext, viewportManager)
    }
}

/**
 * Holds a bitmap and its associated canvas, returned by DrawingPipeline
 * when it creates or updates the offscreen bitmap.
 */
data class BitmapState(val bitmap: Bitmap, val canvas: Canvas)
