package com.wyldsoft.notes.shapemanagement.shapes

import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.NeoBrushPenWrapper
import com.onyx.android.sdk.pen.PenUtils
import com.wyldsoft.notes.rendering.RenderContext

/**
 * Shape for the NEO_BRUSH pen type. Uses the Onyx SDK's NeoBrushPenWrapper to
 * compute pressure-sensitive stroke points, then draws them via
 * PenUtils.drawStrokeByPointSize(). Similar to BrushScribbleShape but
 * uses a different Onyx pen algorithm for a softer brush effect.
 * Created by ShapeFactory for SHAPE_NEO_BRUSH_SCRIBBLE.
 */
class NewBrushScribbleShape : Shape() {
    override fun render(renderContext: RenderContext) {
        val points = touchPointList!!.points
        applyStrokeStyle(renderContext)

        val neoBrushPoints = NeoBrushPenWrapper.computeStrokePoints(
            points,
            strokeWidth, EpdController.getMaxTouchPressure()
        )
        PenUtils.drawStrokeByPointSize(
            renderContext.canvas,
            renderContext.paint,
            neoBrushPoints,
            isTransparent
        )
    }
}
