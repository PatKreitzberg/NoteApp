package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.Log
import com.onyx.android.sdk.pen.PenUtils
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.rendering.RenderContext
/**
 * Base class for all drawable shapes. Stores stroke properties (color, width, type,
 * texture), the raw TouchPointList from the Onyx SDK, and bounding/origin rects
 * for hit testing and partial refresh.
 * 
 * Subclasses override render(RenderContext) to draw themselves using different
 * Onyx pen algorithms (NeoFountainPen, NeoMarkerPen, etc.).
 * 
 * Provides hit testing via hitTestPoints() / hitTest() which compute
 * point-to-line-segment distance for eraser collision detection (used by EraseManager).
 * applyStrokeStyle() configures the Paint in a RenderContext before drawing.
 * 
 * Subclasses: NormalPencilShape, BrushScribbleShape, MarkerScribbleShape,
 * CharcoalScribbleShape, NewBrushScribbleShape.
 */
open class Shape {
    protected var TAG = "Shape"
    var shapeType: Int = 0
    var texture: Int = 0
    var strokeColor: Int = 0
    var strokeWidth: Float = 0f
    var isTransparent: Boolean = false
        protected set

    var touchPointList: TouchPointList? = null

    var boundingRect: RectF? = null
    var originRect: RectF? = null

    fun setTexture(texture: Int): Shape {
        this.texture = texture
        return this
    }

    fun updateShapeRect() {
        Log.d(TAG, "updateShapeRect")
        val list = touchPointList!!.points
        for (touchPoint in list) {
            if (touchPoint == null) {
                continue
            }
            if (originRect == null) {
                originRect = RectF(touchPoint.x, touchPoint.y, touchPoint.x, touchPoint.y)
            } else {
                originRect!!.union(touchPoint.x, touchPoint.y)
            }
        }
        boundingRect = RectF(originRect)
    }

    open fun render(renderContext: RenderContext) {
    }

    /**
     * Temporarily swaps touchPointList to viewport coordinates, renders, then restores.
     * Centralizes the note→viewport coord swap that was duplicated across rendering call sites.
     */
    fun renderInViewport(renderContext: RenderContext, viewportManager: com.wyldsoft.notes.rendering.ViewportManager) {
        val original = touchPointList
        touchPointList = viewportManager.noteToViewportTouchPoints(touchPointList!!)
        try {
            render(renderContext)
        } finally {
            touchPointList = original
        }
    }

    fun applyStrokeStyle(renderContext: RenderContext) {
        val paint = renderContext.paint
        paint.strokeWidth = this.renderStrokeWidth
        paint.setColor(strokeColor)
        paint.isAntiAlias = true
        paint.isDither = false
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeMiter = 4.0f
        paint.setPathEffect(null)
        if (this.isTransparent) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        } else {
            paint.xfermode = null
        }
    }

    val renderStrokeWidth: Float
        get() {
            val strokeWidth = this.strokeWidth
            return if (this.isTransparent) (strokeWidth + PenUtils.ERASE_EXTRA_STROKE_WIDTH) else strokeWidth
        }

    fun hitTestPoints(pointList: TouchPointList, radius: Float): Boolean {
        for (touchPoint in pointList.points) {
            if (hitTest(touchPoint.x, touchPoint.y, radius)) {
                return true
            }
        }
        return false
    }

    private fun hitTest(x: Float, y: Float, radius: Float): Boolean {
        // Option 5: early-exit if point is outside expanded bounding rect
        val br = boundingRect
        if (br != null && (x < br.left - radius || x > br.right + radius ||
                    y < br.top - radius || y > br.bottom + radius)) {
            return false
        }

        val point = floatArrayOf(x, y)
        val invertMatrix = Matrix()
        invertMatrix.mapPoints(point)
        val radiusSq = radius * radius
        val points = touchPointList!!.points
        for (i in 0..<points.size - 1) {
            val distSq = distanceSquared(
                points[i]!!.x, points[i]!!.y,
                points[i + 1]!!.x, points[i + 1]!!.y,
                point[0], point[1]
            )
            if (distSq <= radiusSq) {
                return true
            }
        }
        return false
    }

    private fun distanceSquared(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float): Float {
        val C = x2 - x1
        val D = y2 - y1

        val lenSq = C * C + D * D
        val param = if (lenSq != 0f) {
            ((x - x1) * C + (y - y1) * D) / lenSq
        } else {
            -1.0f
        }

        val xx: Float
        val yy: Float

        if (param < 0) {
            xx = x1
            yy = y1
        } else if (param > 1) {
            xx = x2
            yy = y2
        } else {
            xx = x1 + param * C
            yy = y1 + param * D
        }

        val dx = x - xx
        val dy = y - yy
        return dx * dx + dy * dy
    }
}
