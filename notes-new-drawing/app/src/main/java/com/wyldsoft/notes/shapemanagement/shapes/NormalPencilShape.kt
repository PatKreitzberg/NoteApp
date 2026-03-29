package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import com.wyldsoft.notes.rendering.RenderContext

/**
 * Shape for BALLPEN and PENCIL pen types. Renders strokes as smooth quad
 * Bezier paths through the touch points. This is the default/simplest
 * shape type — no pressure-based width variation or texture effects.
 * Created by ShapeFactory for SHAPE_PENCIL_SCRIBBLE.
 */
class NormalPencilShape : Shape() {
    override fun render(renderContext: RenderContext) {
        val points = touchPointList!!.points
        Log.d("tilt", "Normal pencil shape")
        applyStrokeStyle(renderContext)
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
