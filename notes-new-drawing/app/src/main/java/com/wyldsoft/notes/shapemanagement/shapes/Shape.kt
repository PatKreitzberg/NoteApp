package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.onyx.android.sdk.pen.PenUtils
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.rendering.RenderContext
import kotlin.math.sqrt

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
    protected var shapeType: Int = 0
    protected var texture: Int = 0
    var strokeColor: Int = 0
        protected set
    var strokeWidth: Float = 0f
        protected set
    var isTransparent: Boolean = false
        protected set

    var touchPointList: TouchPointList? = null

    var boundingRect: RectF? = null
    var originRect: RectF? = null

    fun setShapeType(shapeType: Int): Shape {
        this.shapeType = shapeType
        return this
    }

    fun setTexture(texture: Int): Shape {
        this.texture = texture
        return this
    }

    fun setStrokeColor(strokeColor: Int): Shape {
        this.strokeColor = strokeColor
        return this
    }

    fun setStrokeWidth(strokeWidth: Float): Shape {
        this.strokeWidth = strokeWidth
        return this
    }

    fun setTouchPointList(touchPointList: TouchPointList): Shape {
        this.touchPointList = touchPointList
        return this
    }

    fun updateShapeRect() {
        val list = touchPointList!!.getPoints()
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

    fun applyStrokeStyle(renderContext: RenderContext) {
        val paint = renderContext.paint
        paint.setStrokeWidth(this.renderStrokeWidth)
        paint.setColor(strokeColor)
        paint.setAntiAlias(true)
        paint.setDither(true)
        paint.setStyle(Paint.Style.STROKE)
        paint.setStrokeCap(Paint.Cap.ROUND)
        paint.setStrokeJoin(Paint.Join.ROUND)
        paint.setStrokeMiter(4.0f)
        paint.setPathEffect(null)
        if (this.isTransparent) {
            paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.CLEAR))
        } else {
            paint.setXfermode(null)
        }
    }

    val renderStrokeWidth: Float
        get() {
            val strokeWidth = this.strokeWidth
            return if (this.isTransparent) (strokeWidth + PenUtils.ERASE_EXTRA_STROKE_WIDTH) else strokeWidth
        }

    fun hitTestPoints(pointList: TouchPointList, radius: Float): Boolean {
        for (touchPoint in pointList.getPoints()) {
            if (hitTest(touchPoint.x, touchPoint.y, radius)) {
                return true
            }
        }
        return false
    }

    private fun hitTest(x: Float, y: Float, radius: Float): Boolean {
        val limit = radius
        var hit = false
        var first: Int
        var second: Int
        val point = floatArrayOf(x, y)
        val invertMatrix = Matrix()
        invertMatrix.mapPoints(point)
        val points = touchPointList!!.getPoints()
        for (i in 0..<points.size - 1) {
            first = i
            second = i + 1

            val isIntersect = hitTest(
                points.get(first)!!.x,
                points.get(first)!!.y,
                points.get(second)!!.x,
                points.get(second)!!.y,
                point[0], point[1], limit
            )
            if (isIntersect) {
                hit = true
                break
            }
        }
        return hit
    }

    private fun hitTest(
        x1: Float, y1: Float, x2: Float,
        y2: Float, x: Float, y: Float, limit: Float
    ): Boolean {
        val value = distance(x1, y1, x2, y2, x, y)
        return value <= limit
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float): Float {
        val A = x - x1
        val B = y - y1
        val C = x2 - x1
        val D = y2 - y1

        val dot = A * C + B * D
        val lenSq = C * C + D * D
        var param = -1.0f
        if (lenSq != 0f) {
            param = dot / lenSq
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
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
}
