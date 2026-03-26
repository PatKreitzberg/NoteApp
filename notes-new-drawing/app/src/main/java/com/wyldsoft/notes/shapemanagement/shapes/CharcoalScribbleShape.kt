package com.wyldsoft.notes.shapemanagement.shapes

import android.util.Log
import com.onyx.android.sdk.data.PenConstant
import com.onyx.android.sdk.data.note.ShapeCreateArgs
import com.onyx.android.sdk.pen.NeoCharcoalPenV2
import com.onyx.android.sdk.pen.PenRenderArgs
import com.wyldsoft.notes.rendering.RenderContext
import com.wyldsoft.notes.rendering.RenderingUtils
import com.wyldsoft.notes.shapemanagement.ShapeFactory

class CharcoalScribbleShape : Shape() {
    override fun render(renderContext: RenderContext) {
        Log.d("Shape", "render")
        val points = touchPointList.getPoints()
        applyStrokeStyle(renderContext)

        Log.d("Shape", "render 2")
        val renderArgs = PenRenderArgs()
            .setCreateArgs(ShapeCreateArgs())
            .setCanvas(renderContext.canvas)
            .setPenType(ShapeFactory.getCharcoalPenType(texture))
            .setColor(strokeColor)
            .setErase(isTransparent())
            .setPaint(renderContext.paint)
            .setScreenMatrix(RenderingUtils.getPointMatrix(renderContext))

        Log.d("Shape", "charcoal points" + points.size)
        if (strokeWidth <= PenConstant.CHARCOAL_SHAPE_DRAW_NORMAL_SCALE_WIDTH_THRESHOLD) {
            renderArgs.setStrokeWidth(strokeWidth)
                .setPoints(points)
            NeoCharcoalPenV2.drawNormalStroke(renderArgs)
        } else {
            renderArgs.setStrokeWidth(strokeWidth)
                .setPoints(points)
                .setRenderMatrix(RenderingUtils.getPointMatrix(renderContext))
            NeoCharcoalPenV2.drawBigStroke(renderArgs)
        }
    }
}
