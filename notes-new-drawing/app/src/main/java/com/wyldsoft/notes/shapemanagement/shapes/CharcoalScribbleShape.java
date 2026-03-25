package com.wyldsoft.notes.shapemanagement.shapes;

import android.util.Log;

import com.wyldsoft.notes.shapemanagement.ShapeFactory;
import com.wyldsoft.notes.rendering.RenderContext;
import com.wyldsoft.notes.rendering.RenderingUtils;
import com.onyx.android.sdk.data.PenConstant;
import com.onyx.android.sdk.data.note.ShapeCreateArgs;
import com.onyx.android.sdk.data.note.TouchPoint;
import com.onyx.android.sdk.pen.NeoCharcoalPenV2;
import com.onyx.android.sdk.pen.PenRenderArgs;

import java.util.List;

public class CharcoalScribbleShape extends Shape {

    @Override
    public void render(RenderContext renderContext) {
        Log.d("Shape", "render");
        List<TouchPoint> points = touchPointList.getPoints();
        applyStrokeStyle(renderContext);

        Log.d("Shape", "render 2");
        PenRenderArgs renderArgs = new PenRenderArgs()
                .setCreateArgs(new ShapeCreateArgs())
                .setCanvas(renderContext.canvas)
                .setPenType(ShapeFactory.getCharcoalPenType(texture))
                .setColor(strokeColor)
                .setErase(isTransparent())
                .setPaint(renderContext.paint)
                .setScreenMatrix(RenderingUtils.getPointMatrix(renderContext));

        Log.d("Shape", "charcoal points" + points.size());
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
}
