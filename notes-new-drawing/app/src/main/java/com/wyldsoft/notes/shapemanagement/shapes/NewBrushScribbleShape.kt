package com.wyldsoft.notes.shapemanagement.shapes

import android.util.Log
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.NeoBrushPenWrapper
import com.wyldsoft.notes.rendering.RenderContext

/**
 * Shape for the NEO_BRUSH pen type. Uses the Onyx SDK's NeoBrushPenWrapper.drawStroke()
 * for pressure-sensitive brush strokes. Similar to BrushScribbleShape but
 * uses a different pen algorithm for a softer brush effect.
 * Created by ShapeFactory for SHAPE_NEO_BRUSH_SCRIBBLE.
 */
class NewBrushScribbleShape : Shape() {
    override fun render(renderContext: RenderContext) {
        val points = touchPointList!!.points

        applyStrokeStyle(renderContext)
        NeoBrushPenWrapper.drawStroke(
            renderContext.canvas, renderContext.paint, points,
            strokeWidth, EpdController.getMaxTouchPressure(), isTransparent
        )
    }
}
