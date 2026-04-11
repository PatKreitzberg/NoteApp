package com.wyldsoft.notes.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode
import com.wyldsoft.notes.models.PaperTemplate
import com.wyldsoft.notes.shapemanagement.shapes.Shape

/**
 * An RxRequest that redraws a rectangular region of the screen.
 * Creates a temporary bitmap covering only the refresh region,
 * renders the template and intersecting shapes into it, and blits
 * the result to the SurfaceView.
 *
 * Can be enqueued through RxManager to serialize with other
 * rendering operations.
 */
class PartialRefreshRequest(
    private val surfaceView: SurfaceView,
    private val refreshRect: RectF,
    private val shapesToRender: List<Shape>,
    private val viewportManager: ViewportManager,
    private val templateRenderer: TemplateRenderer,
    private val currentTemplate: PaperTemplate,
    private val paginationManager: PaginationManager?,
    private val fullCanvasWidth: Int,
    private val fullCanvasHeight: Int
) : com.onyx.android.sdk.rx.RxRequest() {

    private val TAG = "PartialRefreshRequest"

    override fun execute() {
        Log.d(TAG, "execute")
        val width = refreshRect.width().toInt()
        val height = refreshRect.height().toInt()

        if (width <= 0 || height <= 0) return

        val tempBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(tempBitmap)
        tempCanvas.drawColor(android.graphics.Color.WHITE)

        // Offset so viewport-coord shapes land in the temp bitmap's local space
        tempCanvas.save()
        tempCanvas.translate(-refreshRect.left, -refreshRect.top)

        // Draw the template so erased areas show the correct background
        val pageRects = paginationManager?.let { pm ->
            (0 until pm.pageCount).map { i ->
                android.graphics.RectF(0f, pm.pageTopY(i), pm.pageWidth, pm.pageBottomY(i))
            }
        }
        templateRenderer.drawTemplate(tempCanvas, currentTemplate, viewportManager, fullCanvasWidth, fullCanvasHeight, pageRects)

        val renderContext = RenderContext.createForBitmap(tempBitmap, tempCanvas)

        // Convert viewport refresh rect to note coords for shape intersection test
        val noteRefreshRect = viewportManager.viewportToNote(refreshRect)

        // Render only shapes that intersect with the refresh area (in note coords)
        for (shape in shapesToRender) {
            val shapeBounds = shape.boundingRect
            if (shapeBounds != null && RectF.intersects(shapeBounds, noteRefreshRect)) {
                shape.renderInViewport(renderContext, viewportManager)
            }
        }
        tempCanvas.restore()

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
