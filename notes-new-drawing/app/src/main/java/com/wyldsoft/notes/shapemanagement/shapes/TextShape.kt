package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.rendering.RendererHelper

class TextShape : BaseShape() {

    var text: String = ""
        set(value) { field = value ?: "" }

    var fontSize: Float = 32f

    var fontFamily: String = "sans-serif"
        set(value) { field = value ?: "sans-serif" }

    private fun resolveTypeface(): Typeface {
        return when (fontFamily) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
    }

    override fun render(renderContext: RendererHelper.RenderContext) {
        if (text.isEmpty() || touchPointList == null) return

        val points = touchPointList?.getPoints() ?: return
        if (points.isEmpty()) return

        val canvas = renderContext.canvas
        val paint = renderContext.paint

        paint.reset()
        paint.color = strokeColor
        paint.textSize = fontSize * renderContext.viewportScale
        paint.typeface = resolveTypeface()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL

        val x = points[0].x
        val y = points[0].y

        canvas.drawText(text, x, y, paint)
    }

    override fun updateShapeRect() {
        if (text.isEmpty() || touchPointList == null) {
            originRect = RectF(0f, 0f, 0f, 0f)
            boundingRect = RectF(originRect)
            return
        }

        val points = touchPointList?.getPoints()
        if (points == null || points.isEmpty()) {
            originRect = RectF(0f, 0f, 0f, 0f)
            boundingRect = RectF(originRect)
            return
        }

        val x = points[0].x
        val y = points[0].y

        val measurePaint = Paint()
        measurePaint.textSize = fontSize
        measurePaint.typeface = resolveTypeface()
        val textBounds = Rect()
        measurePaint.getTextBounds(text, 0, text.length, textBounds)

        originRect = RectF(
            x + textBounds.left,
            y + textBounds.top.toFloat(),
            x + textBounds.right,
            y + textBounds.bottom.toFloat()
        )
        boundingRect = RectF(originRect)
    }

    override fun hitTestPoints(pointList: TouchPointList, radius: Float): Boolean {
        updateShapeRect()
        val bRect = boundingRect ?: return false
        val expanded = RectF(
            bRect.left - radius,
            bRect.top - radius,
            bRect.right + radius,
            bRect.bottom + radius
        )
        for (tp in pointList.getPoints()) {
            if (expanded.contains(tp.x, tp.y)) return true
        }
        return false
    }
}
