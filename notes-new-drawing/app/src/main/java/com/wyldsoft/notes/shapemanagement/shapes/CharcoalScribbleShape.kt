package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import com.wyldsoft.notes.shapemanagement.ShapeFactory
import com.wyldsoft.notes.rendering.RendererHelper
import com.wyldsoft.notes.rendering.RenderingUtils
import com.wyldsoft.notes.sdkintegration.DeviceHelper
import com.onyx.android.sdk.data.PenConstant
import com.onyx.android.sdk.data.note.ShapeCreateArgs
import com.onyx.android.sdk.pen.NeoCharcoalPenWrapper
import com.onyx.android.sdk.pen.PenRenderArgs

class CharcoalScribbleShape : BaseShape() {

    override fun render(renderContext: RendererHelper.RenderContext) {
        if (DeviceHelper.isOnyxDevice) {
            renderOnyx(renderContext)
        } else {
            renderGeneric(renderContext)
        }
    }

    private fun renderOnyx(renderContext: RendererHelper.RenderContext) {
        val points = touchPointList!!.getPoints()
        applyStrokeStyle(renderContext)

        val createArgs = ShapeCreateArgs()
        createArgs.maxPressure = getMaxTouchPressure()

        val renderArgs = PenRenderArgs()
            .setCanvas(renderContext.canvas)
            .setPaint(renderContext.paint)
            .setCreateArgs(createArgs)
            .setPenType(ShapeFactory.getCharcoalPenType(texture))
            .setColor(strokeColor)
            .setErase(transparent)
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

    private fun renderGeneric(renderContext: RendererHelper.RenderContext) {
        val points = touchPointList!!.getPoints()
        applyStrokeStyle(renderContext)
        val canvas = renderContext.canvas
        val paint = renderContext.paint

        paint.strokeWidth = strokeWidth * 1.8f
        paint.alpha = 160
        paint.strokeCap = Paint.Cap.BUTT

        val path = Path()
        val prev = PointF(points[0].x, points[0].y)
        path.moveTo(prev.x, prev.y)
        for (point in points) {
            path.quadTo(prev.x, prev.y, point.x, point.y)
            prev.x = point.x
            prev.y = point.y
        }
        canvas.drawPath(path, paint)
    }
}
