package com.wyldsoft.notes.shapemanagement.shapes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.onyx.android.sdk.data.note.TouchPoint;
import com.onyx.android.sdk.pen.data.TouchPointList;
import com.wyldsoft.notes.rendering.RendererHelper;

import java.util.List;

public class TextShape extends BaseShape {

    private String text = "";
    private float fontSize = 32f;
    private String fontFamily = "sans-serif";

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public String getText() {
        return text;
    }

    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
    }

    public float getFontSize() {
        return fontSize;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily != null ? fontFamily : "sans-serif";
    }

    public String getFontFamily() {
        return fontFamily;
    }

    private Typeface resolveTypeface() {
        switch (fontFamily) {
            case "serif":
                return Typeface.SERIF;
            case "monospace":
                return Typeface.MONOSPACE;
            case "sans-serif":
            default:
                return Typeface.SANS_SERIF;
        }
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
        paint.setTextSize(fontSize * renderContext.viewportScale);
        paint.setTypeface(resolveTypeface());
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
        measurePaint.setTextSize(fontSize);
        measurePaint.setTypeface(resolveTypeface());
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
