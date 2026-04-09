package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import com.wyldsoft.notes.rendering.RenderContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometry shape that renders an equilateral triangle outline.
 * touchPointList[0] = centroid, touchPointList[1] = apex vertex.
 * The other two vertices are 120° and 240° from the apex around the centroid.
 */
class TriangleGeometryShape : Shape() {
    companion object {
        private const val TAG = "TriangleGeometryShape"
        private const val TWO_PI_THIRDS = (2.0 * Math.PI / 3.0).toFloat()
    }

    override fun render(renderContext: RenderContext) {
        Log.d(TAG, "render")
        val canvas = renderContext.canvas ?: return
        val pts = touchPointList?.points ?: return
        if (pts.size < 2) return

        val cx = pts[0].x
        val cy = pts[0].y
        val ax = pts[1].x
        val ay = pts[1].y
        val dx = ax - cx
        val dy = ay - cy
        val radius = sqrt(dx * dx + dy * dy)
        if (radius < 1f) return

        val baseAngle = atan2(dy, dx)

        // Three vertices of the equilateral triangle
        val x0 = cx + radius * cos(baseAngle)
        val y0 = cy + radius * sin(baseAngle)
        val x1 = cx + radius * cos(baseAngle + TWO_PI_THIRDS)
        val y1 = cy + radius * sin(baseAngle + TWO_PI_THIRDS)
        val x2 = cx + radius * cos(baseAngle - TWO_PI_THIRDS)
        val y2 = cy + radius * sin(baseAngle - TWO_PI_THIRDS)

        val path = Path().apply {
            moveTo(x0, y0)
            lineTo(x1, y1)
            lineTo(x2, y2)
            close()
        }

        val paint = Paint().apply {
            isAntiAlias = true
            color = if (strokeColor != 0) strokeColor else Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = if (this@TriangleGeometryShape.strokeWidth > 0f) this@TriangleGeometryShape.strokeWidth else 4f
        }
        canvas.drawPath(path, paint)
    }
}
