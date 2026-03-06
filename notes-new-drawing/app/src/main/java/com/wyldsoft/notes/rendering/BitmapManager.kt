package com.wyldsoft.notes.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.rx.RxManager

import com.onyx.android.sdk.data.note.TouchPoint
import com.wyldsoft.notes.domain.models.PaperTemplate
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.shapemanagement.SelectionManager
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.utils.createStrokePaint
import com.wyldsoft.notes.utils.notePointsToSurfaceTouchPoints
import com.wyldsoft.notes.viewport.ViewportManager
import androidx.core.graphics.withSave

/* BitmapManager is responsible for managing the bitmap rendering process.
 * It handles recreating the bitmap from shapes and rendering it to the screen.
 *
 * The bitmap contains only what is currently visible in the viewport.
 */

class BitmapManager(
    private val surfaceView: SurfaceView,
    private val viewModel: EditorViewModel,
    private val rxManager: RxManager?,
    private val getBitmap: () -> Bitmap?,
    private val getBitmapCanvas: () -> Canvas?
) {
    companion object {
        private var TAG = "BitmapManager"
    }
    private val rendererHelper = RendererHelper()
    private val templateRenderer = PaperTemplateRenderer(viewModel.viewportManager)

    /**
     * Recreates the bitmap from the list of drawn shapes.
     * This method clears the existing bitmap and redraws all shapes
     * based on their touch points transformed to surface coordinates.
     */
    fun recreateBitmapFromShapes(setOfShapes: MutableList<BaseShape>?) {
        Log.d("DebugAug11.1", "Recreating bitmap from shapes")
        val bitmap = getBitmap() ?: return
        val canvas = getBitmapCanvas() ?: return

        // Clear the bitmap
        canvas.drawColor(Color.WHITE)

        // Draw paper template background before shapes
        val template = viewModel.paperTemplate.value
        if (template != PaperTemplate.BLANK) {
            templateRenderer.drawTemplate(
                canvas, template, viewModel.screenWidth.value,
                viewModel.isPaginationEnabled.value, viewModel.pageHeight.value
            )
        }

        // Get render context
        val renderContext = rendererHelper.getRenderContext() ?: return
        renderContext.bitmap = bitmap

        // Render all shapes - they are stored in note coordinates, so transform them to surface coordinates
        val viewportManager = viewModel.viewportManager
        val screenWidth = bitmap.width
        val screenHeight = bitmap.height

        if (setOfShapes != null) {
            for (shape in setOfShapes) {
                // Skip shapes that are not visible in the current viewport
                if (!isShapeVisible(shape, viewportManager, screenWidth, screenHeight)) {
                    Log.d("DebugAug11.1", "Skipping shape - not visible in viewport")
                    continue
                }
                Log.d("DebugAug11.1", "Rendering shape with ${shape.touchPointList.size()} points")

                canvas.withSave {

                    // Create a temporary shape with surface coordinates
                    val surfaceTouchPoints = notePointsToSurfaceTouchPoints(shape.touchPointList, viewportManager)

                    // Temporarily replace the shape's touch points
                    val originalTouchPoints = shape.touchPointList
                    shape.touchPointList = surfaceTouchPoints

                    initRenderContext(renderContext, this)

                    shape.render(renderContext)

                    // Restore original touch points
                    shape.touchPointList = originalTouchPoints

                }
            }
        }
        // Note: renderBitmapToScreen() is intentionally not called here.
        // All callers (forceScreenRefresh, surfaceChanged, onNoteSwitched) call renderToScreen()
        // themselves, so calling it here would enqueue a redundant render request.
    }



    /**
     * Checks if a shape is visible in the current viewport
     */
    private fun isShapeVisible(shape: BaseShape, viewportManager: ViewportManager, screenWidth: Int, screenHeight: Int): Boolean {
        // Get the shape's bounding rect
        Log.d("DebugAug11.1", "Checking visibility for shape")
        shape.updateShapeRect()
        val boundingRect = shape.boundingRect ?: return false
        Log.d("DebugAug11.1", "Shape bounding rect: $boundingRect")

        // Convert the bounding rect corners to surface coordinates
        val topLeft = viewportManager.noteToSurfaceCoordinates(boundingRect.left, boundingRect.top)
        val bottomRight = viewportManager.noteToSurfaceCoordinates(boundingRect.right, boundingRect.bottom)
        Log.d("DebugAug11.1", "Shape surface rect: topLeft=$topLeft, bottomRight=$bottomRight")
        // Check if the shape intersects with the screen
        return !(topLeft.x > screenWidth || bottomRight.x < 0 ||
                topLeft.y > screenHeight || bottomRight.y < 0)
    }

    fun clearDrawing() {
        getBitmapCanvas()?.drawColor(Color.WHITE)
        renderBitmapToScreen()
    }

    /**
     * Renders bitmap to screen
     */
    internal fun renderBitmapToScreen() {
        val bitmap = getBitmap() ?: return

        if (rxManager != null) {
            rxManager.enqueue(
                RendererToScreenRequest(
                    surfaceView,
                    bitmap
                ), null)
        } else {
            // Direct rendering for non-Onyx devices
            renderBitmapDirectly(bitmap)
        }
    }

    /**
     * Direct rendering path for non-Onyx devices (no RxManager queue).
     */
    private fun renderBitmapDirectly(bitmap: Bitmap) {
        val viewRect = RenderingUtils.checkSurfaceView(surfaceView) ?: return
        val canvas = surfaceView.holder.lockCanvas() ?: return
        try {
            RenderingUtils.renderBackground(canvas, viewRect)
            RenderingUtils.drawRendererContent(bitmap, canvas)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            surfaceView.holder.unlockCanvasAndPost(canvas)
        }
    }

    /**
     * Renders a shape to the bitmap
     */
    fun renderShapeToBitmap(shape: BaseShape) {
        val bmp = getBitmap() ?: return
        val renderContext = rendererHelper.getRenderContext() ?: return
        val canvas = getBitmapCanvas() ?: return

        // Don't apply viewport transformation here since shape is in surface coordinates
        renderContext.bitmap = bmp
        initRenderContext(renderContext, canvas)

        shape.render(renderContext)
    }

    /**
     * Draws selection overlay (lasso line or bounding box) on top of the current bitmap,
     * then pushes the result to screen.
     */
    fun drawSelectionOverlay(selectionManager: SelectionManager, viewportManager: ViewportManager) {
        val canvas = getBitmapCanvas() ?: return
        if (selectionManager.isLassoInProgress) {
            SelectionRenderer.drawLasso(canvas, selectionManager.getLassoPoints(), viewportManager)
        }
        selectionManager.selectionBoundingBox?.let { box ->
            SelectionRenderer.drawBoundingBox(canvas, box, viewportManager)
            // Draw scale/rotate handles when selection exists
            selectionManager.getHandlePositions()?.let { handles ->
                SelectionRenderer.drawHandles(canvas, handles, viewportManager)
            }
        }
        renderBitmapToScreen()
    }

    /**
     * Draws line segments from [startIdx] to end of [points] onto the bitmap,
     * then pushes the bitmap to screen. Used for real-time drawing feedback
     * on non-Onyx devices.
     */
    fun drawSegmentsToScreen(
        points: List<TouchPoint>,
        startIdx: Int,
        penProfile: PenProfile
    ) {
        val canvas = getBitmapCanvas() ?: return
        val paint = createStrokePaint().apply {
            color = penProfile.getColorAsInt()
            strokeWidth = penProfile.strokeWidth
        }

        val maxPressure = 1.0f
        for (i in startIdx until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val pressure = maxOf(p1.pressure, 0.1f)
            val width = penProfile.strokeWidth * (pressure / maxPressure) * 2.5f
            paint.strokeWidth = maxOf(width, 1f)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }

        renderBitmapToScreen()
    }

    private fun initRenderContext(renderContext: RendererHelper.RenderContext, canvas: Canvas) {
        renderContext.canvas = canvas
        renderContext.paint = createStrokePaint()
        renderContext.viewPoint = android.graphics.Point(0, 0)
    }
}