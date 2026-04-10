package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.wyldsoft.notes.geometry.GeometryShapeRenderer
import com.wyldsoft.notes.rendering.RenderContext
import kotlin.math.sqrt

/**
 * Geometry shape that renders a rotated rectangle outline.
 * touchPointList[0] = center, touchPointList[1] = corner point.
 * Half-diagonal = distance(center, corner). Rotation angle = atan2(dy, dx).
 * The rectangle is axis-aligned relative to the center→corner direction,
 * with aspect ratio [GeometryShapeRenderer.ASPECT_RATIO] (golden ratio).
 */
class RectangleGeometryShape : Shape() {
    companion object {
        private const val TAG = "RectangleGeometryShape"
    }

    override fun updateShapeRect() {
        Log.d(TAG, "updateShapeRect")
        val pts = touchPointList?.points ?: return
        if (pts.size < 2) return
        val cx = pts[0].x; val cy = pts[0].y
        val dx = pts[1].x - cx; val dy = pts[1].y - cy
        // Diagonal from center to corner — the rotated rect always fits inside this circumscribed square
        val diagonal = sqrt(dx * dx + dy * dy)
        val pad = strokeWidth / 2f
        originRect = RectF(cx - diagonal - pad, cy - diagonal - pad, cx + diagonal + pad, cy + diagonal + pad)
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
            strokeWidth = if (this@RectangleGeometryShape.strokeWidth > 0f) this@RectangleGeometryShape.strokeWidth else 4f
        }
        GeometryShapeRenderer.drawRectangle(canvas, pts[0].x, pts[0].y, pts[1].x, pts[1].y, paint)
    }
}
