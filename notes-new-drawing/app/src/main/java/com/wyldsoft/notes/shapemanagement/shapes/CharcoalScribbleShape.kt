package com.wyldsoft.notes.shapemanagement.shapes

import android.util.Log
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.data.PenConstant
import com.onyx.android.sdk.data.note.ShapeCreateArgs
import com.onyx.android.sdk.pen.NeoCharcoalPenWrapper
import com.onyx.android.sdk.pen.PenRenderArgs
import com.wyldsoft.notes.rendering.RenderContext
import com.wyldsoft.notes.rendering.RenderingUtils
import com.wyldsoft.notes.shapemanagement.ShapeFactory

/**
 * Shape for CHARCOAL and CHARCOAL_V2 pen types. Uses the Onyx SDK's
 * NeoCharcoalPenWrapper with PenRenderArgs, choosing drawNormalStroke() or
 * drawBigStroke() based on stroke width threshold. Requires a coordinate
 * transform matrix from RenderingUtils.getPointMatrix() for rendering.
 * Created by ShapeFactory for SHAPE_CHARCOAL_SCRIBBLE.
 */
class CharcoalScribbleShape : Shape() {
    override fun render(renderContext: RenderContext) {
        val points = touchPointList!!.points
        Log.d(TAG, "CharcoalScribbleShape")

        for (p in points){
            Log.d("tilt", p.tiltX.toString())
        }


        applyStrokeStyle(renderContext)

        val createArgs = ShapeCreateArgs()
        createArgs.maxPressure = EpdController.getMaxTouchPressure()

        val renderArgs = PenRenderArgs()
            .setCanvas(renderContext.canvas)
            .setPaint(renderContext.paint)
            .setCreateArgs(createArgs)
            .setPenType(ShapeFactory.getCharcoalPenType(texture))
            .setColor(strokeColor)
            .setErase(isTransparent)
            .setTiltEnabled(true)
            .setScreenMatrix(RenderingUtils.getPointMatrix(renderContext))

        if (strokeWidth <= PenConstant.CHARCOAL_SHAPE_DRAW_NORMAL_SCALE_WIDTH_THRESHOLD) {
            renderArgs.setStrokeWidth(strokeWidth)
                .setPoints(points)
            NeoCharcoalPenWrapper.drawNormalStroke(renderArgs)
        } else {
            renderArgs.setStrokeWidth(strokeWidth)
                .setPoints(points)
                .setRenderMatrix(RenderingUtils.getPointMatrix(renderContext))
            NeoCharcoalPenWrapper.drawBigStroke(renderArgs)
        }
    }
}
