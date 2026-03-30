package com.wyldsoft.notes.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.util.Log

/**
 * Bag of drawing resources passed to Shape.render().
 * Holds the target Bitmap, its Canvas, a shared Paint, and a viewPoint
 * offset for coordinate translation. Created fresh by OnyxDrawingActivity
 * (renderShapeToBitmap / recreateBitmapFromShapes) and by
 * PartialEraseRefresh before rendering each frame.
 */
class RenderContext {
    @JvmField var paint = Paint()
    @JvmField var bitmap: Bitmap? = null
    @JvmField var canvas: Canvas? = null
    @JvmField var viewPoint: Point? = null

    companion object {
        private val TAG = "RenderContext"

        fun createForBitmap(bitmap: Bitmap, canvas: Canvas): RenderContext {
            Log.d(TAG, "createForBitmap")
            return RenderContext().apply {
                this.bitmap = bitmap
                this.canvas = canvas
                paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                viewPoint = Point(0, 0)
            }
        }
    }
}
