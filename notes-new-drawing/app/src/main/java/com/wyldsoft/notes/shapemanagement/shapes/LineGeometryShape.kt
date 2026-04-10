package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.wyldsoft.notes.geometry.GeometryShapeRenderer
import com.wyldsoft.notes.rendering.RenderContext
import kotlin.math.max
import kotlin.math.min

/**
 * Geometry shape that renders a straight line.
 * touchPointList[0] = start, touchPointList[1] = end.
 */
class LineGeometryShape : Shape() {
    companion object {
        private const val TAG = "LineGeometryShape"
    }

    override fun updateShapeRect() {
        Log.d(TAG, "updateShapeRect")
        val pts = touchPointList?.points ?: return
        if (pts.size < 2) return
        val pad = max(strokeWidth / 2f, 4f)
        originRect = RectF(
            min(pts[0].x, pts[1].x) - pad,
            min(pts[0].y, pts[1].y) - pad,
            max(pts[0].x, pts[1].x) + pad,
            max(pts[0].y, pts[1].y) + pad
        )
        boundingRect = RectF(originRect)
    }

    override fun render(renderContext: RenderContext) {
        Log.d(TAG, "render")
        val canvas = renderContext.canvas ?: return
        val pts = touchPointList?.points ?: return
        if (pts.size < 2) return

        val paint = Paint().apply {
            isAntiAlias = true
            color = if (strokeColor != 0) strokeColor else Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = if (this@LineGeometryShape.strokeWidth > 0f) this@LineGeometryShape.strokeWidth else 4f
            strokeCap = Paint.Cap.ROUND
        }
        GeometryShapeRenderer.drawLine(canvas, pts[0].x, pts[0].y, pts[1].x, pts[1].y, paint)
    }
}
