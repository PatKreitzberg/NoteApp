package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import com.wyldsoft.notes.rendering.RendererHelper

class NormalPencilShape : BaseShape() {

    override fun render(renderContext: RendererHelper.RenderContext) {
        Log.d("NormalPencilShape", "render")
        val points = touchPointList!!.getPoints()

        Log.d("NormalPencilShape", "renderContext.paint.color: ${renderContext.paint.color}")
        applyStrokeStyle(renderContext)
        val canvas = renderContext.canvas
        val paint = renderContext.paint
        Log.d("NormalPencilShape", "paint.color: ${paint.color}")
        val path = Path()
        val prePoint = PointF(points[0].x, points[0].y)
        path.moveTo(prePoint.x, prePoint.y)
        for (point in points) {
            path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
            prePoint.x = point.x
            prePoint.y = point.y
        }

        Log.d("NormalPencilShape", "draw paint.color: ${paint.color}")
        paint.strokeWidth = 30.0F
        canvas.drawPath(path, paint)
    }
}
