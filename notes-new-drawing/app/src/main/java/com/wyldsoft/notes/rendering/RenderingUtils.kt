package com.wyldsoft.notes.rendering

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.view.SurfaceView

/**
 * Static helpers for surface rendering.
 * - checkSurfaceView: validates a SurfaceView's holder and returns its bounds.
 * - renderBackground / clearBackground: fills the canvas with white.
 * - getPointMatrix: builds a translation Matrix from RenderContext.viewPoint,
 * used by CharcoalScribbleShape for coordinate transforms during rendering.
 * 
 * Called by RendererToScreenRequest and CharcoalScribbleShape.
 */
object RenderingUtils {
    @JvmStatic
    fun renderBackground(
        canvas: Canvas,
        viewRect: Rect
    ) {
        clearBackground(canvas, Paint(), viewRect)
    }


    @JvmStatic
    fun checkSurfaceView(surfaceView: SurfaceView?): Rect? {
        if (surfaceView == null || !surfaceView.holder.surface.isValid) {
            return null
        }
        return Rect(0, 0, surfaceView.width, surfaceView.height)
    }

    fun clearBackground(canvas: Canvas, paint: Paint, rect: Rect) {
        paint.style = Paint.Style.FILL
        paint.setColor(Color.WHITE)
        canvas.drawRect(rect, paint)
    }

    fun getPointMatrix(renderContext: RenderContext): Matrix {
        val anchorPoint = renderContext.viewPoint
        val matrix = Matrix()
        matrix.postTranslate(anchorPoint!!.x.toFloat(), anchorPoint.y.toFloat())
        return matrix
    }
}