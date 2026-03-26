package com.wyldsoft.notes.shapemanagement.shapes

import android.util.Log
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.NeoFountainPen
import com.onyx.android.sdk.pen.PenUtils
import com.onyx.android.sdk.utils.NumberUtils
import com.wyldsoft.notes.rendering.RenderContext

class BrushScribbleShape : Shape() {
    override fun render(renderContext: RenderContext) {
        val points = touchPointList.getPoints()
        applyStrokeStyle(renderContext)
        val brushPoints = NeoFountainPen.computeStrokePoints(
            points,
            NumberUtils.FLOAT_ONE, strokeWidth, EpdController.getMaxTouchPressure()
        )
        PenUtils.drawStrokeByPointSize(
            renderContext.canvas,
            renderContext.paint,
            brushPoints,
            isTransparent()
        )
        Log.d("Shape", "brushPoints" + brushPoints.size)
    }
}
