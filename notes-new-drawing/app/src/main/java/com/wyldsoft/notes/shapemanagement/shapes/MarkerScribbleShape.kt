package com.wyldsoft.notes.shapemanagement.shapes

import android.util.Log
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.NeoMarkerPen
import com.wyldsoft.notes.rendering.RenderContext

class MarkerScribbleShape : Shape() {
    override fun render(renderContext: RenderContext) {
        val points = touchPointList.getPoints()
        applyStrokeStyle(renderContext)
        val markerPoints = NeoMarkerPen.computeStrokePoints(
            points, strokeWidth,
            EpdController.getMaxTouchPressure()
        )
        NeoMarkerPen.drawStroke(
            renderContext.canvas,
            renderContext.paint,
            markerPoints,
            strokeWidth,
            isTransparent()
        )
        Log.d("Shape", "markerPoints" + markerPoints)
    }
}
