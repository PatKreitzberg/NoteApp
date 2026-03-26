package com.wyldsoft.notes.shapemanagement.shapes

import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.NeoFountainPenWrapper
import com.onyx.android.sdk.pen.PenUtils
import com.wyldsoft.notes.rendering.RenderContext

/**
 * Shape for the FOUNTAIN pen type. Delegates rendering to the Onyx SDK's
 * NeoFountainPenWrapper which computes pressure-sensitive variable-width stroke
 * points, then draws them via PenUtils.drawStrokeByPointSize().
 * Created by ShapeFactory for SHAPE_BRUSH_SCRIBBLE.
 */
class BrushScribbleShape : Shape() {
    override fun render(renderContext: RenderContext) {
        val points = touchPointList!!.points
        applyStrokeStyle(renderContext)

        val brushPoints = NeoFountainPenWrapper.computeStrokePoints(
            points,
            strokeWidth, EpdController.getMaxTouchPressure()
        )

        PenUtils.drawStrokeByPointSize(
            renderContext.canvas,
            renderContext.paint,
            brushPoints,
            isTransparent
        )
    }
}
