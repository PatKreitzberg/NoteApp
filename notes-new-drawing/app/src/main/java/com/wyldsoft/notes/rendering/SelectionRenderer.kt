package com.wyldsoft.notes.rendering

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.wyldsoft.notes.shapemanagement.HandlePositions
import com.wyldsoft.notes.viewport.ViewportManager

/**
 * Draws selection visuals (lasso line, bounding box, and transform handles) onto the bitmap canvas.
 */
object SelectionRenderer {

    private const val HANDLE_HALF_SIZE = 8f  // half-width of corner squares
    private const val ROTATION_CIRCLE_RADIUS = 10f

    private val lassoPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
        isAntiAlias = true
    }

    private val boundingBoxPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        isAntiAlias = true
    }

    private val handlePaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val handleStrokePaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    /**
     * Draws the lasso path (dashed grey line) on the canvas.
     * [lassoPoints] are in note coordinates; they are converted to surface coordinates for drawing.
     */
    fun drawLasso(canvas: Canvas, lassoPoints: List<PointF>, viewportManager: ViewportManager) {
        if (lassoPoints.size < 2) return
        val path = Path()
        val first = viewportManager.noteToSurfaceCoordinates(lassoPoints[0].x, lassoPoints[0].y)
        path.moveTo(first.x, first.y)
        for (i in 1 until lassoPoints.size) {
            val pt = viewportManager.noteToSurfaceCoordinates(lassoPoints[i].x, lassoPoints[i].y)
            path.lineTo(pt.x, pt.y)
        }
        canvas.drawPath(path, lassoPaint)
    }

    /**
     * Draws a dashed bounding box around the selected shapes.
     * [boundingBox] is in note coordinates.
     */
    fun drawBoundingBox(canvas: Canvas, boundingBox: RectF, viewportManager: ViewportManager) {
        val topLeft = viewportManager.noteToSurfaceCoordinates(boundingBox.left, boundingBox.top)
        val bottomRight = viewportManager.noteToSurfaceCoordinates(boundingBox.right, boundingBox.bottom)
        val surfaceRect = RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
        canvas.drawRect(surfaceRect, boundingBoxPaint)
    }

    /**
     * Draws scale handles (corner squares) and rotation handle (circle above top-center).
     * [handlePositions] are in note coordinates.
     */
    fun drawHandles(canvas: Canvas, handlePositions: HandlePositions, viewportManager: ViewportManager) {
        // Draw corner scale handles (filled squares)
        for (corner in handlePositions.corners) {
            val surfacePt = viewportManager.noteToSurfaceCoordinates(corner.x, corner.y)
            canvas.drawRect(
                surfacePt.x - HANDLE_HALF_SIZE,
                surfacePt.y - HANDLE_HALF_SIZE,
                surfacePt.x + HANDLE_HALF_SIZE,
                surfacePt.y + HANDLE_HALF_SIZE,
                handlePaint
            )
        }

        // Draw line from top-center of box to rotation handle
        val topCenter = PointF(
            (handlePositions.corners[0].x + handlePositions.corners[1].x) / 2f,
            handlePositions.corners[0].y
        )
        val surfaceTopCenter = viewportManager.noteToSurfaceCoordinates(topCenter.x, topCenter.y)
        val surfaceRotHandle = viewportManager.noteToSurfaceCoordinates(
            handlePositions.rotationHandle.x, handlePositions.rotationHandle.y
        )
        canvas.drawLine(surfaceTopCenter.x, surfaceTopCenter.y, surfaceRotHandle.x, surfaceRotHandle.y, handleStrokePaint)

        // Draw rotation handle (filled circle)
        canvas.drawCircle(surfaceRotHandle.x, surfaceRotHandle.y, ROTATION_CIRCLE_RADIUS, handlePaint)
    }
}
