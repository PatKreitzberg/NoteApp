package com.wyldsoft.notes.shapemanagement.shapes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

import com.onyx.android.sdk.data.note.TouchPoint;
import com.onyx.android.sdk.pen.NeoMarkerPenWrapper;
import com.wyldsoft.notes.rendering.RendererHelper;
import com.wyldsoft.notes.sdkintegration.DeviceHelper;
import android.util.Log;
import java.util.List;

public class MarkerScribbleShape extends BaseShape {

    @Override
    public void render(RendererHelper.RenderContext renderContext) {
        if (DeviceHelper.INSTANCE.isOnyxDevice()) {
            renderOnyx(renderContext);
        } else {
            renderGeneric(renderContext);
        }
    }

    private void renderOnyx(RendererHelper.RenderContext renderContext) {
        Log.d("MarkerScribbleShape", "renderOnyx MarkerScribbleShape");
        List<TouchPoint> points = touchPointList.getPoints();

        for (int i = 0; i < points.size(); i++) {
            TouchPoint point = points.get(i);

            // Extract common data points
            float x = point.getX();
            float y = point.getY();
            float pressure = point.getPressure();
            long timestamp = point.getTimestamp();
            float tiltX = point.getTiltX();
            float tiltY = point.getTiltY();

            Log.d("MarkerScribbleShape", String.format("Point [%d]: tiltX=%.2f, tiltY=%.2f, pressure=%.2f, time=%d",
                    i, tiltX, tiltY, pressure, timestamp));
        }

        applyStrokeStyle(renderContext);
        NeoMarkerPenWrapper.drawStroke(renderContext.canvas, renderContext.paint, points, strokeWidth, isTransparent());
    }

    /** Wide semi-transparent stroke for non-Onyx devices */
    private void renderGeneric(RendererHelper.RenderContext renderContext) {
        List<TouchPoint> points = touchPointList.getPoints();
        applyStrokeStyle(renderContext);
        Canvas canvas = renderContext.canvas;
        Paint paint = renderContext.paint;

        // Marker effect: wider stroke with alpha
        paint.setStrokeWidth(strokeWidth * 2.5f);
        paint.setAlpha(80);
        paint.setStrokeCap(Paint.Cap.SQUARE);

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
