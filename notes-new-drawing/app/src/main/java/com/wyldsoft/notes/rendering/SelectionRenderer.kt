package com.wyldsoft.notes.rendering

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.wyldsoft.notes.viewport.ViewportManager

/**
 * Draws selection visuals (lasso line and bounding box) onto the bitmap canvas.
 */
object SelectionRenderer {

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
}
