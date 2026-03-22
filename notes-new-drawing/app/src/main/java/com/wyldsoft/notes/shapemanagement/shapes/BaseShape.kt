package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.wyldsoft.notes.rendering.RendererHelper
import com.wyldsoft.notes.sdkintegration.DeviceHelper
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import java.util.UUID

open class BaseShape(
    var touchPointList: TouchPointList? = null
) {
    var shapeType: Int = 0
        protected set
    var texture: Int = 0
        protected set
    var strokeColor: Int = 0
    var strokeWidth: Float = 0f
    var id: String = UUID.randomUUID().toString()
    var transparent: Boolean = false
    var layer: Int = 1

    var boundingRect: RectF? = null
    var originRect: RectF? = null
        protected set

    fun setShapeType(shapeType: Int): BaseShape {
        this.shapeType = shapeType
        return this
    }

    fun setTexture(texture: Int): BaseShape {
        this.texture = texture
        return this
    }

    fun setStrokeColor(strokeColor: Int): BaseShape {
        this.strokeColor = strokeColor
        return this
    }

    fun setStrokeWidth(strokeWidth: Float): BaseShape {
        this.strokeWidth = strokeWidth
        return this
    }

    fun setTouchPointList(touchPointList: TouchPointList): BaseShape {
        this.touchPointList = touchPointList
        return this
    }

    open fun updateShapeRect() {
        originRect = null

        val list = touchPointList?.getPoints() ?: return
        for (touchPoint in list) {
            if (touchPoint == null) continue
            val rect = originRect
            if (rect == null) {
                originRect = RectF(touchPoint.x, touchPoint.y, touchPoint.x, touchPoint.y)
            } else {
                rect.union(touchPoint.x, touchPoint.y)
            }
        }
        boundingRect = RectF(originRect)
    }

    open fun render(renderContext: RendererHelper.RenderContext) {}

    fun applyStrokeStyle(renderContext: RendererHelper.RenderContext) {
        val paint = renderContext.paint
        paint.strokeWidth = renderStrokeWidth
        paint.color = strokeColor
        paint.isAntiAlias = true
        paint.isDither = false
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeMiter = 4.0f
        paint.pathEffect = null
        if (transparent) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        } else {
            paint.xfermode = null
        }
    }

    val renderStrokeWidth: Float
        get() = if (transparent) strokeWidth + ERASE_EXTRA_STROKE_WIDTH else strokeWidth

    open fun hitTestPoints(pointList: TouchPointList, radius: Float): Boolean {
        if (boundingRect == null) {
            updateShapeRect()
        }
        val bRect = boundingRect
        if (bRect != null) {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (tp in pointList.getPoints()) {
                val px = tp.x
                val py = tp.y
                if (px < minX) minX = px
                if (py < minY) minY = py
                if (px > maxX) maxX = px
                if (py > maxY) maxY = py
            }
            if (minX - radius > bRect.right || maxX + radius < bRect.left ||
                minY - radius > bRect.bottom || maxY + radius < bRect.top
            ) {
                return false
            }
        }

        for (touchPoint in pointList.getPoints()) {
            if (hitTest(touchPoint.x, touchPoint.y, radius)) {
                return true
            }
        }
        return false
    }

    private fun hitTest(x: Float, y: Float, radius: Float): Boolean {
        val point = floatArrayOf(x, y)
        val invertMatrix = Matrix()
        invertMatrix.mapPoints(point)
        val points = touchPointList?.getPoints() ?: return false
        for (i in 0 until points.size - 1) {
            val isIntersect = hitTestSegment(
                points[i].x, points[i].y,
                points[i + 1].x, points[i + 1].y,
                point[0], point[1], radius
            )
            if (isIntersect) return true
        }
        return false
    }

    private fun hitTestSegment(
        x1: Float, y1: Float, x2: Float, y2: Float,
        x: Float, y: Float, limit: Float
    ): Boolean {
        val value = distanceSquared(x1, y1, x2, y2, x, y)
        return value <= limit * limit
    }

    private fun distanceSquared(
        x1: Float, y1: Float, x2: Float, y2: Float,
        x: Float, y: Float
    ): Float {
        val a = x - x1
        val b = y - y1
        val c = x2 - x1
        val d = y2 - y1

        val dot = a * c + b * d
        val lenSq = c * c + d * d
        var param = -1.0f
        if (lenSq != 0f) {
            param = dot / lenSq
        }

        val xx: Float
        val yy: Float

        if (param < 0) {
            xx = x1; yy = y1
        } else if (param > 1) {
            xx = x2; yy = y2
        } else {
            xx = x1 + param * c; yy = y1 + param * d
        }

        val dx = x - xx
        val dy = y - yy
        return dx * dx + dy * dy
    }

    companion object {
        private const val ERASE_EXTRA_STROKE_WIDTH = 5f
        const val DEFAULT_MAX_TOUCH_PRESSURE = 4096f

        @JvmStatic
        fun getMaxTouchPressure(): Float {
            return if (DeviceHelper.isOnyxDevice) {
                com.onyx.android.sdk.api.device.epd.EpdController.getMaxTouchPressure()
            } else {
                DEFAULT_MAX_TOUCH_PRESSURE
            }
        }
    }
}
