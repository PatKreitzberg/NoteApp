package com.wyldsoft.notes.shapemanagement.shapes

import android.util.Log
import com.onyx.android.sdk.pen.NeoMarkerPenWrapper
import com.wyldsoft.notes.rendering.RenderContext

/**
 * Shape for the MARKER pen type. Uses the Onyx SDK's NeoMarkerPenWrapper.drawStroke()
 * for wide, flat marker-style strokes with pressure sensitivity.
 * Created by ShapeFactory for SHAPE_MARKER_SCRIBBLE.
 */
class MarkerScribbleShape : Shape() {
    override fun render(renderContext: RenderContext) {
        val points = touchPointList!!.points

        for (p in points){
            Log.d("tilt", p.tiltX.toString())
        }

        applyStrokeStyle(renderContext)
        NeoMarkerPenWrapper.drawStroke(
            renderContext.canvas, renderContext.paint, points,
            strokeWidth, isTransparent
        )
    }
}
