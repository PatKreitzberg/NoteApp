package com.wyldsoft.notes.rendering;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.SurfaceView;
import android.view.View;

import com.onyx.android.sdk.api.device.epd.EpdController;
import com.wyldsoft.notes.sdkintegration.DeviceHelper;

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

    public static void drawRendererContent(Bitmap bitmap, Canvas canvas) {
        Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        canvas.drawBitmap(bitmap, rect, rect, null);
    }

    /**
     * Signals the e-ink controller to allow pending screen updates on the given view.
     * Must be called before unlocking a canvas or enqueuing a refresh request.
     */
    public static void enableScreenPost(View view) {
        if (DeviceHelper.INSTANCE.isOnyxDevice()) {
            EpdController.enablePost(view, 1);
        }
    }

    public static Matrix getPointMatrix(final RendererHelper.RenderContext renderContext) {
        Point anchorPoint = renderContext.viewPoint;
        Matrix matrix = new Matrix();
        matrix.postTranslate(anchorPoint.x, anchorPoint.y);
        return matrix;
    }

}