package com.wyldsoft.notes.shapemanagement.shapes

import com.onyx.android.sdk.data.PenConstant
import com.onyx.android.sdk.data.note.ShapeCreateArgs
import com.onyx.android.sdk.pen.NeoCharcoalPenV2Wrapper
import com.onyx.android.sdk.pen.PenRenderArgs
import com.wyldsoft.notes.rendering.RenderContext
import com.wyldsoft.notes.rendering.RenderingUtils
import com.wyldsoft.notes.shapemanagement.ShapeFactory

/**
 * Shape for CHARCOAL and CHARCOAL_V2 pen types. Uses the Onyx SDK's
 * NeoCharcoalPenV2Wrapper with PenRenderArgs, choosing drawNormalStroke() or
 * drawBigStroke() based on stroke width threshold. Requires a coordinate
 * transform matrix from RenderingUtils.getPointMatrix() for rendering.
 * Created by ShapeFactory for SHAPE_CHARCOAL_SCRIBBLE.
 */
class CharcoalScribbleShape : Shape() {
    override fun render(renderContext: RenderContext) {
        val points = touchPointList!!.points
        applyStrokeStyle(renderContext)

        val renderArgs = PenRenderArgs()
            .setCreateArgs(ShapeCreateArgs())
            .setCanvas(renderContext.canvas)
            .setPenType(ShapeFactory.getCharcoalPenType(texture))
            .setColor(strokeColor)
            .setErase(isTransparent)
            .setPaint(renderContext.paint)
            .setScreenMatrix(RenderingUtils.getPointMatrix(renderContext))

        if (strokeWidth <= PenConstant.CHARCOAL_SHAPE_DRAW_NORMAL_SCALE_WIDTH_THRESHOLD) {
            renderArgs.setStrokeWidth(strokeWidth).points = points
            NeoCharcoalPenV2Wrapper.drawNormalStroke(renderArgs)
        } else {
            renderArgs.setStrokeWidth(strokeWidth)
                .setPoints(points).renderMatrix = RenderingUtils.getPointMatrix(renderContext)
            NeoCharcoalPenV2Wrapper.drawBigStroke(renderArgs)
        }
    }
}
