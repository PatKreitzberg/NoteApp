package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import com.wyldsoft.notes.rendering.RenderContext
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Geometry shape that renders a rotated rectangle outline.
 * touchPointList[0] = center, touchPointList[1] = corner point.
 * Half-diagonal = distance(center, corner). Rotation angle = atan2(dy, dx).
 * The rectangle is axis-aligned relative to the center→corner direction,
 * with aspect ratio 1.618 (golden ratio) so it looks like a proper rectangle.
 */
class RectangleGeometryShape : Shape() {
    companion object {
        private const val TAG = "RectangleGeometryShape"
        private const val ASPECT_RATIO = 1.618f
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
        val diagonal = sqrt(dx * dx + dy * dy)
        if (diagonal < 1f) return

        // Compute half-width and half-height from diagonal and aspect ratio
        // diagonal² = halfW² + halfH², halfW = halfH * ASPECT_RATIO
        // halfH = diagonal / sqrt(1 + ASPECT_RATIO²)
        val halfH = diagonal / sqrt(1f + ASPECT_RATIO * ASPECT_RATIO)
        val halfW = halfH * ASPECT_RATIO

        // Build an unrotated rectangle centered at origin
        val rect = RectF(-halfW, -halfH, halfW, halfH)
        val path = Path().apply { addRect(rect, Path.Direction.CW) }

        // Rotate to align with center→corner direction
        val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val matrix = Matrix()
        matrix.postRotate(angleDeg)
        matrix.postTranslate(cx, cy)
        path.transform(matrix)

        val paint = Paint().apply {
            isAntiAlias = true
            color = if (strokeColor != 0) strokeColor else Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = if (this@RectangleGeometryShape.strokeWidth > 0f) this@RectangleGeometryShape.strokeWidth else 4f
        }
        canvas.drawPath(path, paint)
    }
}
