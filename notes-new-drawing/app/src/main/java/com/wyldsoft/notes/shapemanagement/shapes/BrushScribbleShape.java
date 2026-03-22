package com.wyldsoft.notes.shapemanagement.shapes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;

import com.onyx.android.sdk.data.note.TouchPoint;
import com.onyx.android.sdk.pen.NeoFountainPenWrapper;
import com.wyldsoft.notes.rendering.RendererHelper;
import com.wyldsoft.notes.sdkintegration.DeviceHelper;

import java.util.List;

public class BrushScribbleShape extends BaseShape {

    @Override
    public void render(RendererHelper.RenderContext renderContext) {
        if (DeviceHelper.INSTANCE.isOnyxDevice()) {
            renderOnyx(renderContext);
        } else {
            renderGeneric(renderContext);
        }
    }

    private void renderOnyx(RendererHelper.RenderContext renderContext) {
        Log.d("BrushScribbleShape", "renderOnyx");
        List<TouchPoint> points = touchPointList.getPoints();
        applyStrokeStyle(renderContext);
        NeoFountainPenWrapper.drawStroke(renderContext.canvas, renderContext.paint, points,
                1.0f, strokeWidth, getMaxTouchPressure(), isTransparent());
    }

    /** Variable-width path based on pressure for non-Onyx devices */
    private void renderGeneric(RendererHelper.RenderContext renderContext) {
        List<TouchPoint> points = touchPointList.getPoints();
        applyStrokeStyle(renderContext);
        Canvas canvas = renderContext.canvas;
        Paint paint = renderContext.paint;

        float maxPressure = DEFAULT_MAX_TOUCH_PRESSURE;
        for (int i = 0; i < points.size() - 1; i++) {
            TouchPoint p1 = points.get(i);
            TouchPoint p2 = points.get(i + 1);
            float pressure = Math.max(p1.pressure, 0.1f);
            float width = strokeWidth * (pressure / maxPressure) * 2f;
            width = Math.max(width, 1f);
            paint.setStrokeWidth(width);
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint);
        }
    }
}
