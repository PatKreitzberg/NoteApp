package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.wyldsoft.notes.rendering.RenderContext

/**
 * Geometry shape that renders a straight line.
 * touchPointList[0] = start, touchPointList[1] = end.
 */
class LineGeometryShape : Shape() {
    companion object {
        private const val TAG = "LineGeometryShape"
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
        canvas.drawLine(pts[0].x, pts[0].y, pts[1].x, pts[1].y, paint)
    }
}
