package com.wyldsoft.notes.geometry

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Single source of truth for all geometry shape drawing math.
 * Used by the geometry Shape subclasses for rendering and by OnyxDrawingActivity
 * for live preview and lasso hit-test outline point generation.
 *
 * All draw functions take (center/startX, center/startY, endX, endY) matching
 * the two-point convention used by the shape classes (pts[0] = anchor, pts[1] = edge).
 */
object GeometryShapeRenderer {
    private const val TAG = "GeometryShapeRenderer"
    const val ASPECT_RATIO = 1.618f
    private val TWO_PI_THIRDS = (2.0 * Math.PI / 3.0).toFloat()

    /** Dispatch to the appropriate draw function based on [shapeType]. */
    fun draw(
        canvas: Canvas,
        shapeType: GeometryShapeType,
        sx: Float, sy: Float,
        ex: Float, ey: Float,
        paint: Paint
    ) {
        Log.d(TAG, "draw shapeType=$shapeType")
        when (shapeType) {
            GeometryShapeType.CIRCLE    -> drawCircle(canvas, sx, sy, ex, ey, paint)
            GeometryShapeType.LINE      -> drawLine(canvas, sx, sy, ex, ey, paint)
            GeometryShapeType.RECTANGLE -> drawRectangle(canvas, sx, sy, ex, ey, paint)
            GeometryShapeType.TRIANGLE  -> drawTriangle(canvas, sx, sy, ex, ey, paint)
        }
    }

    /**
     * Draw a circle centered at (cx, cy) with radius = distance to (ex, ey).
     */
    fun drawCircle(canvas: Canvas, cx: Float, cy: Float, ex: Float, ey: Float, paint: Paint) {
        val dx = ex - cx; val dy = ey - cy
        val radius = sqrt(dx * dx + dy * dy)
        if (radius < 1f) return
        canvas.drawCircle(cx, cy, radius, paint)
    }

    /**
     * Draw a straight line from (sx, sy) to (ex, ey).
     */
    fun drawLine(canvas: Canvas, sx: Float, sy: Float, ex: Float, ey: Float, paint: Paint) {
        canvas.drawLine(sx, sy, ex, ey, paint)
    }

    /**
     * Draw a rotated rectangle centered at (cx, cy) with one corner at (ex, ey).
     * Aspect ratio is [ASPECT_RATIO] (golden ratio).
     */
    fun drawRectangle(canvas: Canvas, cx: Float, cy: Float, ex: Float, ey: Float, paint: Paint) {
        val dx = ex - cx; val dy = ey - cy
        val diagonal = sqrt(dx * dx + dy * dy)
        if (diagonal < 1f) return

        val halfH = diagonal / sqrt(1f + ASPECT_RATIO * ASPECT_RATIO)
        val halfW = halfH * ASPECT_RATIO
        val path = Path().apply {
            addRect(RectF(-halfW, -halfH, halfW, halfH), Path.Direction.CW)
        }
        val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val matrix = Matrix()
        matrix.postRotate(angleDeg)
        matrix.postTranslate(cx, cy)
        path.transform(matrix)
        canvas.drawPath(path, paint)
    }

    /**
     * Draw an equilateral triangle with centroid at (cx, cy) and apex at (ax, ay).
     * The other two vertices are 120° and 240° from the apex around the centroid.
     */
    fun drawTriangle(canvas: Canvas, cx: Float, cy: Float, ax: Float, ay: Float, paint: Paint) {
        val dx = ax - cx; val dy = ay - cy
        val radius = sqrt(dx * dx + dy * dy)
        if (radius < 1f) return

        val baseAngle = atan2(dy, dx)
        val x0 = cx + radius * cos(baseAngle)
        val y0 = cy + radius * sin(baseAngle)
        val x1 = cx + radius * cos(baseAngle + TWO_PI_THIRDS)
        val y1 = cy + radius * sin(baseAngle + TWO_PI_THIRDS)
        val x2 = cx + radius * cos(baseAngle - TWO_PI_THIRDS)
        val y2 = cy + radius * sin(baseAngle - TWO_PI_THIRDS)

        val path = Path().apply {
            moveTo(x0, y0); lineTo(x1, y1); lineTo(x2, y2); close()
        }
        canvas.drawPath(path, paint)
    }

    /**
     * Append representative boundary points to [tpl] for lasso hit-testing.
     * The anchor is (sx, sy) and the edge/corner/apex is (ex, ey).
     * render() in each shape class only reads pts[0] and pts[1], so these
     * extra points do not affect drawing.
     */
    fun appendOutlinePoints(
        tpl: TouchPointList,
        shapeType: GeometryShapeType,
        sx: Float, sy: Float,
        ex: Float, ey: Float,
        ts: Long
    ) {
        Log.d(TAG, "appendOutlinePoints shapeType=$shapeType")
        val dx = ex - sx; val dy = ey - sy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 1f) return

        fun addPt(x: Float, y: Float) {
            tpl.add(TouchPoint(x, y, 1f, 0f, 0, 0, ts))
        }

        when (shapeType) {
            GeometryShapeType.CIRCLE -> {
                val steps = 24
                for (i in 0 until steps) {
                    val angle = i * 2.0 * Math.PI / steps
                    addPt(
                        sx + dist * cos(angle).toFloat(),
                        sy + dist * sin(angle).toFloat()
                    )
                }
            }
            GeometryShapeType.LINE -> {
                // The 2 endpoints already fully define the lasso extent
            }
            GeometryShapeType.RECTANGLE -> {
                val halfH = dist / sqrt(1f + ASPECT_RATIO * ASPECT_RATIO)
                val halfW = halfH * ASPECT_RATIO
                val angle = atan2(dy, dx)
                val cosA = cos(angle); val sinA = sin(angle)
                for ((lx, ly) in listOf(
                    Pair(+halfW, +halfH), Pair(-halfW, +halfH),
                    Pair(-halfW, -halfH), Pair(+halfW, -halfH)
                )) {
                    addPt(sx + lx * cosA - ly * sinA, sy + lx * sinA + ly * cosA)
                }
            }
            GeometryShapeType.TRIANGLE -> {
                val baseAngle = atan2(dy, dx)
                for (i in 0..2) {
                    val a = baseAngle + i * TWO_PI_THIRDS
                    addPt(
                        sx + dist * cos(a).toFloat(),
                        sy + dist * sin(a).toFloat()
                    )
                }
            }
        }
    }
}
