package com.wyldsoft.notes.refreshingscreen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.SurfaceView
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.rendering.RendererHelper
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.utils.createStrokePaint

class PartialEraseRefresh {
    
    fun performPartialRefresh(
        surfaceView: SurfaceView,
        refreshRect: RectF,
        remainingShapes: List<BaseShape>,
        rendererHelper: RendererHelper,
        rxManager: RxManager
    ) {
        // Create a partial refresh request for the erased area
        val partialRefreshRequest = PartialRefreshRequest(
            surfaceView,
            refreshRect,
            remainingShapes,
            rendererHelper
        )
        //
        EpdController.enablePost(surfaceView, 1);
        rxManager.enqueue(partialRefreshRequest, null)
    }
    
    private class PartialRefreshRequest(
        private val surfaceView: SurfaceView,
        private val refreshRect: RectF,
        private val shapesToRender: List<BaseShape>,
        private val rendererHelper: RendererHelper
    ) : com.onyx.android.sdk.rx.RxRequest() {
        
        override fun execute() {
            // Create a temporary bitmap for the refresh area
            val width = refreshRect.width().toInt()
            val height = refreshRect.height().toInt()
            
            if (width <= 0 || height <= 0) return
            
            val tempBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val tempCanvas = Canvas(tempBitmap)
            
            // Clear the area (white background)
            tempCanvas.drawColor(android.graphics.Color.WHITE)
            
            // Set up render context
            val renderContext = rendererHelper.getRenderContext()
            renderContext.bitmap = tempBitmap
            renderContext.canvas = tempCanvas
            renderContext.paint = createStrokePaint()
            renderContext.viewPoint = android.graphics.Point(-refreshRect.left.toInt(), -refreshRect.top.toInt())
            
            // Render only shapes that intersect with the refresh area
            for (shape in shapesToRender) {
                val shapeBounds = shape.boundingRect
                if (shapeBounds != null && RectF.intersects(shapeBounds, refreshRect)) {
                    shape.render(renderContext)
                }
            }
            
            // Render the temporary bitmap to the surface
            renderToSurface(tempBitmap)
            
            // Clean up
            tempBitmap.recycle()
        }
        
        private fun renderToSurface(bitmap: Bitmap) {
            val holder = surfaceView.holder
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
                }
            }
        }
    }
}