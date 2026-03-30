package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.DashPathEffect
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import com.wyldsoft.notes.rendering.RenderContext

/**
 * Shape for DASH pen type. Renders quad Bezier paths like NormalPencilShape
 * but with a DashPathEffect applied so the stroke appears dashed on bitmap.
 * The dash pattern scales with stroke width for consistent appearance.
 */
class DashPencilShape : Shape() {
    private val tag = "DashPencilShape"

    override fun render(renderContext: RenderContext) {
        Log.d(tag, "render")
        val points = touchPointList!!.points
        applyStrokeStyle(renderContext)
        val dashLength = strokeWidth * 3f
        val gapLength = strokeWidth * 2f
        renderContext.paint.pathEffect = DashPathEffect(floatArrayOf(dashLength, gapLength), 0f)
        val canvas = renderContext.canvas
        val paint = renderContext.paint
        val path = Path()
        val prePoint = PointF(points[0].x, points[0].y)
        path.moveTo(prePoint.x, prePoint.y)
        for (point in points) {
            path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
            prePoint.x = point.x
            prePoint.y = point.y
        }
        canvas!!.drawPath(path, paint)
    }
}
