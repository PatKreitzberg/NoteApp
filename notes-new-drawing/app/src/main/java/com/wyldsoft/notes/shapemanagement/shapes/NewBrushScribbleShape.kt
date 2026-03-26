package com.wyldsoft.notes.shapemanagement.shapes

import android.util.Log
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.NeoBrushPen
import com.onyx.android.sdk.pen.PenUtils
import com.wyldsoft.notes.rendering.RenderContext

/**
 * Shape for the NEO_BRUSH pen type. Uses the Onyx SDK's NeoBrushPen to
 * compute pressure-sensitive stroke points, then draws them via
 * PenUtils.drawStrokeByPointSize(). Similar to BrushScribbleShape but
 * uses a different Onyx pen algorithm for a softer brush effect.
 * Created by ShapeFactory for SHAPE_NEO_BRUSH_SCRIBBLE.
 */
class NewBrushScribbleShape : Shape() {
    override fun render(renderContext: RenderContext) {
        val points = touchPointList.getPoints()
        applyStrokeStyle(renderContext)

        val NeoBrushPoints = NeoBrushPen.computeStrokePoints(
            points,
            strokeWidth, EpdController.getMaxTouchPressure()
        )
        PenUtils.drawStrokeByPointSize(
            renderContext.canvas,
            renderContext.paint,
            NeoBrushPoints,
            isTransparent()
        )
        Log.d("Shape", "neoBrushPoints.size()" + NeoBrushPoints.size)
    }
}
