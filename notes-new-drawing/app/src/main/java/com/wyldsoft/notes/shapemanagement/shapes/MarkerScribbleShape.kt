package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import com.onyx.android.sdk.pen.NeoMarkerPenWrapper
import com.wyldsoft.notes.rendering.RendererHelper
import com.wyldsoft.notes.sdkintegration.DeviceHelper

class MarkerScribbleShape : BaseShape() {

    override fun render(renderContext: RendererHelper.RenderContext) {
        if (DeviceHelper.isOnyxDevice) {
            renderOnyx(renderContext)
        } else {
            renderGeneric(renderContext)
        }
    }

    private fun renderOnyx(renderContext: RendererHelper.RenderContext) {
        Log.d("MarkerScribbleShape", "renderOnyx MarkerScribbleShape")
        val points = touchPointList!!.getPoints()

        for (i in points.indices) {
            val point = points[i]
            val x = point.x
            val y = point.y
            val pressure = point.pressure
            val timestamp = point.timestamp
            val tiltX = point.tiltX
            val tiltY = point.tiltY

            Log.d(
                "MarkerScribbleShape",
                String.format(
                    "Point [%d]: tiltX=%.2f, tiltY=%.2f, pressure=%.2f, time=%d",
                    i, tiltX.toFloat(), tiltY.toFloat(), pressure, timestamp
                )
            )
        }

        applyStrokeStyle(renderContext)
        NeoMarkerPenWrapper.drawStroke(
            renderContext.canvas, renderContext.paint, points, strokeWidth, transparent
        )
    }

    private fun renderGeneric(renderContext: RendererHelper.RenderContext) {
        val points = touchPointList!!.getPoints()
        applyStrokeStyle(renderContext)
        val canvas = renderContext.canvas
        val paint = renderContext.paint

        paint.strokeWidth = strokeWidth * 2.5f
        paint.alpha = 80
        paint.strokeCap = Paint.Cap.SQUARE

        val path = Path()
        val prev = PointF(points[0].x, points[0].y)
        path.moveTo(prev.x, prev.y)
        for (point in points) {
            path.quadTo(prev.x, prev.y, point.x, point.y)
            prev.x = point.x
            prev.y = point.y
        }
        canvas.drawPath(path, paint)
    }
}
