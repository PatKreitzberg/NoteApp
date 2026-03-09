package com.wyldsoft.notes.shapemanagement.shapes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.onyx.android.sdk.data.note.TouchPoint;
import com.onyx.android.sdk.pen.data.TouchPointList;
import com.wyldsoft.notes.rendering.RendererHelper;

import java.util.List;

public class TextShape extends BaseShape {

    private static final float TEXT_SIZE = 32f;
    private String text = "";

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public String getText() {
        return text;
    }

    @Override
    public void render(RendererHelper.RenderContext renderContext) {
        if (text.isEmpty() || touchPointList == null) return;

        List<TouchPoint> points = touchPointList.getPoints();
        if (points == null || points.isEmpty()) return;

        Canvas canvas = renderContext.canvas;
        Paint paint = renderContext.paint;

        paint.reset();
        paint.setColor(strokeColor);
        paint.setTextSize(TEXT_SIZE * (strokeWidth / 2f));
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        float x = points.get(0).x;
        float y = points.get(0).y;

        canvas.drawText(text, x, y, paint);
    }

    @Override
    public void updateShapeRect() {
        if (text.isEmpty() || touchPointList == null) {
            originRect = new RectF(0, 0, 0, 0);
            boundingRect = new RectF(originRect);
            return;
        }

        List<TouchPoint> points = touchPointList.getPoints();
        if (points == null || points.isEmpty()) {
            originRect = new RectF(0, 0, 0, 0);
            boundingRect = new RectF(originRect);
            return;
        }

        float x = points.get(0).x;
        float y = points.get(0).y;

        Paint measurePaint = new Paint();
        measurePaint.setTextSize(TEXT_SIZE * (strokeWidth / 2f));
        Rect textBounds = new Rect();
        measurePaint.getTextBounds(text, 0, text.length(), textBounds);

        originRect = new RectF(
                x + textBounds.left,
                y + textBounds.top,
                x + textBounds.right,
                y + textBounds.bottom
        );
        boundingRect = new RectF(originRect);
    }

    @Override
    public boolean hitTestPoints(TouchPointList pointList, float radius) {
        updateShapeRect();
        if (boundingRect == null) return false;
        RectF expanded = new RectF(
                boundingRect.left - radius,
                boundingRect.top - radius,
                boundingRect.right + radius,
                boundingRect.bottom + radius
        );
        for (TouchPoint tp : pointList.getPoints()) {
            if (expanded.contains(tp.x, tp.y)) return true;
        }
        return false;
    }
}
