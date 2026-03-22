package com.wyldsoft.notes.shapemanagement.shapes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.Log;

import com.onyx.android.sdk.data.note.TouchPoint;
import com.wyldsoft.notes.rendering.RendererHelper;


import java.util.List;

public class NormalPencilShape extends BaseShape {

    @Override
    public void render(RendererHelper.RenderContext renderContext) {
        Log.d("NormalPencilShape", "render");
        List<TouchPoint> points = touchPointList.getPoints();
        applyStrokeStyle(renderContext);
        Canvas canvas = renderContext.canvas;
        Paint paint = renderContext.paint;
        Path path = new Path();
        PointF prePoint = new PointF(points.get(0).x, points.get(0).y);
        path.moveTo(prePoint.x, prePoint.y);
        for (TouchPoint point : points) {
            path.quadTo(prePoint.x, prePoint.y, point.x, point.y);
            prePoint.x = point.x;
            prePoint.y = point.y;
        }
        canvas.drawPath(path, paint);
    }
}
