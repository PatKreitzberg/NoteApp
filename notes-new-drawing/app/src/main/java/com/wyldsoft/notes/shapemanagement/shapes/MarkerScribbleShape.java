package com.wyldsoft.notes.shapemanagement.shapes;

import com.onyx.android.sdk.api.device.epd.EpdController;
import com.onyx.android.sdk.data.note.TouchPoint;
import com.onyx.android.sdk.pen.NeoMarkerPen;
import com.wyldsoft.notes.rendering.RenderContext;
import android.util.Log;
import java.util.List;

public class MarkerScribbleShape extends Shape {

    @Override
    public void render(RenderContext renderContext) {

        List<TouchPoint> points = touchPointList.getPoints();
        applyStrokeStyle(renderContext);
        List<TouchPoint> markerPoints = NeoMarkerPen.computeStrokePoints(points, strokeWidth,
                EpdController.getMaxTouchPressure());
        NeoMarkerPen.drawStroke(renderContext.canvas, renderContext.paint, markerPoints, strokeWidth, isTransparent());
        Log.d("Shape", "markerPoints" + markerPoints);
    }
}
