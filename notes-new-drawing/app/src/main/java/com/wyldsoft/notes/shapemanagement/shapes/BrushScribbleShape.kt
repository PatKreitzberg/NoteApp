package com.wyldsoft.notes.shapemanagement.shapes

import android.util.Log
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.NeoFountainPenWrapper
import com.wyldsoft.notes.rendering.RenderContext

/**
 * Shape for the FOUNTAIN pen type. Delegates rendering to the Onyx SDK's
 * NeoFountainPenWrapper.drawStroke() which handles pressure-sensitive
 * variable-width stroke rendering.
 * Created by ShapeFactory for SHAPE_BRUSH_SCRIBBLE.
 */
class BrushScribbleShape : Shape() {
    override fun render(renderContext: RenderContext) {
        val points = touchPointList!!.points

        applyStrokeStyle(renderContext)
        NeoFountainPenWrapper.drawStroke(
            renderContext.canvas, renderContext.paint, points,
            1.0f, strokeWidth, EpdController.getMaxTouchPressure(), isTransparent
        )
    }
}
