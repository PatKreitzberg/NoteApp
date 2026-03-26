package com.wyldsoft.notes.shapemanagement.shapes

import android.util.Log
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.NeoBrushPen
import com.onyx.android.sdk.pen.PenUtils
import com.wyldsoft.notes.rendering.RenderContext

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
