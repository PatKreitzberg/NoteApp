package com.wyldsoft.notes.refreshingscreen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.rendering.RenderContext
import com.wyldsoft.notes.shapemanagement.shapes.Shape

/**
 * Handles efficient partial screen redraws after shapes are erased.
 * Instead of re-rendering the entire bitmap, it creates a temporary bitmap
 * covering only the erased region, re-renders the surviving shapes that
 * intersect that region, and blits the result to the SurfaceView.
 *
 * Called by OnyxDrawingActivity.handleErasing() after EraseManager identifies
 * which shapes were hit. The refresh request is enqueued through RxManager
 * to serialize it with other rendering operations.
 */
class PartialEraseRefresh {
    protected var TAG = "PartialEraseRefresh"
    fun performPartialRefresh(
        surfaceView: SurfaceView,
        refreshRect: RectF,
        remainingShapes: List<Shape>,
        rxManager: RxManager
    ) {
        Log.d(TAG, "performPartialRefresh")

        EpdController.enablePost(surfaceView, 1)
        val partialRefreshRequest = PartialRefreshRequest(
            surfaceView,
            refreshRect,
            remainingShapes
        )
        rxManager.enqueue(partialRefreshRequest, null)
    }

    private class PartialRefreshRequest(
        private val surfaceView: SurfaceView,
        private val refreshRect: RectF,
        private val shapesToRender: List<Shape>
    ) : com.onyx.android.sdk.rx.RxRequest() {

        protected var TAG = "PartialRefreshRequest"

        override fun execute() {
            Log.d(TAG, "execute")
            val width = refreshRect.width().toInt()
            val height = refreshRect.height().toInt()

            if (width <= 0 || height <= 0) return

            val tempBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val tempCanvas = Canvas(tempBitmap)
            tempCanvas.drawColor(android.graphics.Color.WHITE)

            val renderContext = RenderContext().apply {
                bitmap = tempBitmap
                canvas = tempCanvas
                paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                viewPoint = android.graphics.Point(-refreshRect.left.toInt(), -refreshRect.top.toInt())
            }

            // Render only shapes that intersect with the refresh area
            for (shape in shapesToRender) {
                val shapeBounds = shape.boundingRect
                if (shapeBounds != null && RectF.intersects(shapeBounds, refreshRect)) {
                    shape.render(renderContext)
                }
            }

            // Render the temporary bitmap to the surface
            EpdController.enablePost(surfaceView, 1)
            renderToSurface(tempBitmap)

            // Clean up
            tempBitmap.recycle()
        }

        private fun renderToSurface(bitmap: Bitmap) {
            Log.d(TAG, "renderToSurface")
            val holder = surfaceView.holder
            EpdController.setViewDefaultUpdateMode(surfaceView, UpdateMode.HAND_WRITING_REPAINT_MODE)
            val canvas = holder.lockCanvas(
                android.graphics.Rect(
                    refreshRect.left.toInt(),
                    refreshRect.top.toInt(),
                    refreshRect.right.toInt(),
                    refreshRect.bottom.toInt()
                )
            )

            if (canvas != null) {
                try {
                    canvas.drawBitmap(
                        bitmap,
                        refreshRect.left,
                        refreshRect.top,
                        null
                    )
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                    EpdController.resetViewUpdateMode(surfaceView)
                }
            } else {
                EpdController.resetViewUpdateMode(surfaceView)
            }
        }
    }
}
