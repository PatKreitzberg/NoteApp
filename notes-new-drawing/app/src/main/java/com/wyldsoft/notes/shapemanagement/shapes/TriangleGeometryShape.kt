package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.wyldsoft.notes.geometry.GeometryShapeRenderer
import com.wyldsoft.notes.rendering.RenderContext
import kotlin.math.sqrt

/**
 * Geometry shape that renders an equilateral triangle outline.
 * touchPointList[0] = centroid, touchPointList[1] = apex vertex.
 * The other two vertices are 120° and 240° from the apex around the centroid.
 */
class TriangleGeometryShape : Shape() {
    companion object {
        private const val TAG = "TriangleGeometryShape"
    }

    override fun updateShapeRect() {
        Log.d(TAG, "updateShapeRect")
        val pts = touchPointList?.points ?: return
        if (pts.size < 2) return
        val cx = pts[0].x; val cy = pts[0].y
        val dx = pts[1].x - cx; val dy = pts[1].y - cy
        // All 3 vertices are at distance `radius` from center, so circumscribed circle bounds the triangle
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

        val paint = Paint().apply {
            isAntiAlias = true
            color = if (strokeColor != 0) strokeColor else Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = if (this@TriangleGeometryShape.strokeWidth > 0f) this@TriangleGeometryShape.strokeWidth else 4f
        }
        GeometryShapeRenderer.drawTriangle(canvas, pts[0].x, pts[0].y, pts[1].x, pts[1].y, paint)
    }
}
