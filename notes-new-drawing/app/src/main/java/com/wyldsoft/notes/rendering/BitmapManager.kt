package com.wyldsoft.notes.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager

import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
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
    private val rxManager: RxManager,
    private val getBitmap: () -> Bitmap?,
    private val getBitmapCanvas: () -> Canvas?
) {
    companion object {
        private var TAG = "BitmapManager"
    }
    private val rendererHelper = RendererHelper()

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
                    val surfaceTouchPoints = TouchPointList()
                    for (i in 0 until shape.touchPointList.size()) {
                        val notePoint = shape.touchPointList.get(i)
                        Log.d(TAG, "notePoint ${notePoint.x}, ${notePoint.y}")

                        val surfacePoint =
                            viewportManager.noteToSurfaceCoordinates(notePoint.x, notePoint.y)
                        surfaceTouchPoints.add(
                            TouchPoint(
                                surfacePoint.x,
                                surfacePoint.y,
                                notePoint.pressure,
                                notePoint.size,
                                notePoint.timestamp
                            )
                        )
                    }

                    // Temporarily replace the shape's touch points
                    val originalTouchPoints = shape.touchPointList
                    shape.touchPointList = surfaceTouchPoints

                    renderContext.canvas = this
                    renderContext.paint = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    renderContext.viewPoint = android.graphics.Point(0, 0)

                    shape.render(renderContext)

                    // Restore original touch points
                    shape.touchPointList = originalTouchPoints

                }
            }
        }

        // Render the updated bitmap to screen
        renderBitmapToScreen()
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

        rxManager.enqueue(
            RendererToScreenRequest(
                surfaceView,
                bitmap
            ), null)
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
        renderContext.canvas = canvas
        renderContext.paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        // Initialize viewPoint for shapes that need it (like CharcoalScribbleShape)
        renderContext.viewPoint = android.graphics.Point(0, 0)

        shape.render(renderContext)
    }
}