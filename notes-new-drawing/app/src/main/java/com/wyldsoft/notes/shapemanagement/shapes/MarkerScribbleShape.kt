package com.wyldsoft.notes.shapemanagement.shapes

import android.util.Log
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.NeoMarkerPen
import com.wyldsoft.notes.rendering.RenderContext

/**
 * Shape for the MARKER pen type. Uses the Onyx SDK's NeoMarkerPen to compute
 * wide, flat marker-style strokes with pressure sensitivity, then draws them
 * via NeoMarkerPen.drawStroke().
 * Created by ShapeFactory for SHAPE_MARKER_SCRIBBLE.
 */
class MarkerScribbleShape : Shape() {
    override fun render(renderContext: RenderContext) {
        val points = touchPointList!!.getPoints()
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
            isTransparent
        )
        Log.d("Shape", "markerPoints" + markerPoints)
    }
}
