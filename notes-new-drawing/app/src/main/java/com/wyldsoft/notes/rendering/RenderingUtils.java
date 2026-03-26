package com.wyldsoft.notes.rendering;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.SurfaceView;

/**
 * Static helpers for surface rendering.
 * - checkSurfaceView: validates a SurfaceView's holder and returns its bounds.
 * - renderBackground / clearBackground: fills the canvas with white.
 * - getPointMatrix: builds a translation Matrix from RenderContext.viewPoint,
 *   used by CharcoalScribbleShape for coordinate transforms during rendering.
 *
 * Called by RendererToScreenRequest and CharcoalScribbleShape.
 */
public class RenderingUtils {

    public static void renderBackground(Canvas canvas,
                                        Rect viewRect) {
        RenderingUtils.clearBackground(canvas, new Paint(), viewRect);
    }


    public static Rect checkSurfaceView(SurfaceView surfaceView) {
        if (surfaceView == null || !surfaceView.getHolder().getSurface().isValid()) {
            return null;
        }
        return new Rect(0, 0, surfaceView.getWidth(), surfaceView.getHeight());
    }

    public static void clearBackground(final Canvas canvas, final Paint paint, final Rect rect) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawRect(rect, paint);
    }

    public static Matrix getPointMatrix(final RenderContext renderContext) {
        Point anchorPoint = renderContext.viewPoint;
        Matrix matrix = new Matrix();
        matrix.postTranslate(anchorPoint.x, anchorPoint.y);
        return matrix;
    }

}