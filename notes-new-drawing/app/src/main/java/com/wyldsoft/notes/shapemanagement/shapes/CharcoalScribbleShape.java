package com.wyldsoft.notes.shapemanagement.shapes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.Log;

import com.wyldsoft.notes.shapemanagement.ShapeFactory;
import com.wyldsoft.notes.rendering.RendererHelper;
import com.wyldsoft.notes.rendering.RenderingUtils;
import com.wyldsoft.notes.sdkintegration.DeviceHelper;
import com.onyx.android.sdk.data.PenConstant;
import com.onyx.android.sdk.data.note.ShapeCreateArgs;
import com.onyx.android.sdk.data.note.TouchPoint;
import com.onyx.android.sdk.pen.NeoCharcoalPenV2;
import com.onyx.android.sdk.pen.PenRenderArgs;

import java.util.List;

public class CharcoalScribbleShape extends BaseShape {

    @Override
    public void render(RendererHelper.RenderContext renderContext) {
        if (DeviceHelper.INSTANCE.isOnyxDevice()) {
            renderOnyx(renderContext);
        } else {
            renderGeneric(renderContext);
        }
    }

    private void renderOnyx(RendererHelper.RenderContext renderContext) {
        List<TouchPoint> points = touchPointList.getPoints();
        applyStrokeStyle(renderContext);

        PenRenderArgs renderArgs = new PenRenderArgs()
                .setCreateArgs(new ShapeCreateArgs())
                .setCanvas(renderContext.canvas)
                .setPenType(ShapeFactory.getCharcoalPenType(texture))
                .setColor(strokeColor)
                .setErase(isTransparent())
                .setPaint(renderContext.paint)
                .setScreenMatrix(RenderingUtils.getPointMatrix(renderContext));

        if (strokeWidth <= PenConstant.CHARCOAL_SHAPE_DRAW_NORMAL_SCALE_WIDTH_THRESHOLD) {
            renderArgs.setStrokeWidth(strokeWidth)
                    .setPoints(points);
            NeoCharcoalPenV2.drawNormalStroke(renderArgs);
        } else {
            renderArgs.setStrokeWidth(strokeWidth)
                    .setPoints(points)
                    .setRenderMatrix(RenderingUtils.getPointMatrix(renderContext));
            NeoCharcoalPenV2.drawBigStroke(renderArgs);
        }
    }

    /** Textured wide stroke fallback for non-Onyx devices */
    private void renderGeneric(RendererHelper.RenderContext renderContext) {
        List<TouchPoint> points = touchPointList.getPoints();
        applyStrokeStyle(renderContext);
        Canvas canvas = renderContext.canvas;
        Paint paint = renderContext.paint;

        // Charcoal effect: wide, semi-transparent strokes with rough edges
        paint.setStrokeWidth(strokeWidth * 1.8f);
        paint.setAlpha(160);
        paint.setStrokeCap(Paint.Cap.BUTT);

        Path path = new Path();
        PointF prev = new PointF(points.get(0).x, points.get(0).y);
        path.moveTo(prev.x, prev.y);
        for (TouchPoint point : points) {
            path.quadTo(prev.x, prev.y, point.x, point.y);
            prev.x = point.x;
            prev.y = point.y;
        }
        canvas.drawPath(path, paint);
    }
}
