package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.wyldsoft.notes.rendering.RenderContext
import kotlin.math.sqrt

/**
 * Geometry shape that renders a circle outline.
 * touchPointList[0] = center, touchPointList[1] = edge point.
 * radius = distance(center, edge).
 */
class CircleGeometryShape : Shape() {
    companion object {
        private const val TAG = "CircleGeometryShape"
    }

    override fun updateShapeRect() {
        Log.d(TAG, "updateShapeRect")
        val pts = touchPointList?.points ?: return
        if (pts.size < 2) return
        val cx = pts[0].x; val cy = pts[0].y
        val dx = pts[1].x - cx; val dy = pts[1].y - cy
        val radius = sqrt(dx * dx + dy * dy)
        val pad = strokeWidth / 2f
        originRect = RectF(cx - radius - pad, cy - radius - pad, cx + radius + pad, cy + radius + pad)
        boundingRect = RectF(originRect)
    }

    override fun render(renderContext: RenderContext) {
        Log.d(TAG, "render")
        val canvas = renderContext.canvas ?: return
        val pts = touchPointList?.points ?: return
        if (pts.size < 2) return

        val cx = pts[0].x
        val cy = pts[0].y
        val ex = pts[1].x
        val ey = pts[1].y
        val dx = ex - cx
        val dy = ey - cy
        val radius = sqrt(dx * dx + dy * dy)
        if (radius < 1f) return

        val paint = Paint().apply {
            isAntiAlias = true
            color = if (strokeColor != 0) strokeColor else Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = if (this@CircleGeometryShape.strokeWidth > 0f) this@CircleGeometryShape.strokeWidth else 4f
        }
        canvas.drawCircle(cx, cy, radius, paint)
    }
}
