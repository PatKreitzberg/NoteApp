package com.wyldsoft.notes.shapemanagement.shapes

import android.util.Log
import com.onyx.android.sdk.pen.NeoFountainPenWrapper
import com.wyldsoft.notes.rendering.RendererHelper
import com.wyldsoft.notes.sdkintegration.DeviceHelper

class BrushScribbleShape : BaseShape() {

    override fun render(renderContext: RendererHelper.RenderContext) {
        if (DeviceHelper.isOnyxDevice) {
            renderOnyx(renderContext)
        } else {
            renderGeneric(renderContext)
        }
    }

    private fun renderOnyx(renderContext: RendererHelper.RenderContext) {
        Log.d("BrushScribbleShape", "renderOnyx BrushScribbleShape")
        val points = touchPointList!!.getPoints()
        applyStrokeStyle(renderContext)
        NeoFountainPenWrapper.drawStroke(
            renderContext.canvas, renderContext.paint, points,
            1.0f, strokeWidth, getMaxTouchPressure(), transparent
        )
    }

    private fun renderGeneric(renderContext: RendererHelper.RenderContext) {
        val points = touchPointList!!.getPoints()
        applyStrokeStyle(renderContext)
        val canvas = renderContext.canvas
        val paint = renderContext.paint

        val maxPressure = DEFAULT_MAX_TOUCH_PRESSURE
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val pressure = maxOf(p1.pressure, 0.1f)
            val width = maxOf(strokeWidth * (pressure / maxPressure) * 2f, 1f)
            paint.strokeWidth = width
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }
    }
}
