package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import com.onyx.android.sdk.pen.NeoMarkerPenWrapper

import com.onyx.android.sdk.pen.BallpointPenRenderWrapper

import com.wyldsoft.notes.rendering.RendererHelper
import com.wyldsoft.notes.sdkintegration.DeviceHelper
import kotlin.collections.get

class MarkerScribbleShape : BaseShape() {
val TAG="MarkerScribbleShape"
    override fun render(renderContext: RendererHelper.RenderContext) {
        if (DeviceHelper.isOnyxDevice) {
            renderOnyx(renderContext)
        } else {
            renderGeneric(renderContext)
        }
    }

    private fun renderOnyx(renderContext: RendererHelper.RenderContext) {
        Log.d("MarkerScribbleShape", "renderOnyx MarkerScribbleShape")
        applyStrokeStyle(renderContext)

        val points = touchPointList!!.getPoints()

        Log.d("MarkerScribbleShape", "Marker: color=${renderContext.paint.color} strokeWidth=${renderContext.paint.strokeWidth} or ${strokeWidth} canvas=${renderContext.canvas.density}")

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
