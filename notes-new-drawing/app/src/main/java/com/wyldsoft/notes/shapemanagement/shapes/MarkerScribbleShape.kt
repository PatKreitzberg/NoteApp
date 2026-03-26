package com.wyldsoft.notes.shapemanagement.shapes

import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.NeoMarkerPenWrapper
import com.wyldsoft.notes.rendering.RenderContext

/**
 * Shape for the MARKER pen type. Uses the Onyx SDK's NeoMarkerPenWrapper to compute
 * wide, flat marker-style strokes with pressure sensitivity, then draws them
 * via NeoMarkerPenWrapper.drawStroke().
 * Created by ShapeFactory for SHAPE_MARKER_SCRIBBLE.
 */
class MarkerScribbleShape : Shape() {
    override fun render(renderContext: RenderContext) {
        val points = touchPointList!!.points
        applyStrokeStyle(renderContext)
        val markerPoints = NeoMarkerPenWrapper.computeStrokePoints(
            points, strokeWidth,
            EpdController.getMaxTouchPressure()
        )
        NeoMarkerPenWrapper.drawStroke(
            renderContext.canvas,
            renderContext.paint,
            markerPoints,
            strokeWidth,
            isTransparent
        )
    }
}
