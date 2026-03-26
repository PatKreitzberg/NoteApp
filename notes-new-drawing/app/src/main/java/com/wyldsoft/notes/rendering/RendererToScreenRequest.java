package com.wyldsoft.notes.rendering;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.SurfaceView;

import com.onyx.android.sdk.api.device.epd.EpdController;
import com.onyx.android.sdk.api.device.epd.UpdateMode;
import com.onyx.android.sdk.rx.RxRequest;

/**
 * RxRequest that blits a finished bitmap to the SurfaceView.
 * Enqueued via RxManager by OnyxDrawingActivity.renderToScreen() to serialize
 * screen updates with other drawing operations. Sets the Onyx e-ink update mode
 * to HAND_WRITING_REPAINT_MODE for optimized partial refresh, clears the surface
 * with a white background via RenderingUtils, then draws the bitmap.
 */
public class RendererToScreenRequest extends RxRequest {
    private SurfaceView surfaceView;
    private Bitmap bitmap;

    public RendererToScreenRequest(SurfaceView surfaceView, Bitmap bitmap) {
        this.surfaceView = surfaceView;
        this.bitmap = bitmap;
    }

    @Override
    public void execute() throws Exception {
        renderToScreen(surfaceView, bitmap);
    }

    private void renderToScreen(SurfaceView surfaceView, Bitmap bitmap) {
        if (surfaceView == null) {
            return;
        }
        Rect viewRect = RenderingUtils.checkSurfaceView(surfaceView);
        EpdController.setViewDefaultUpdateMode(surfaceView, UpdateMode.HAND_WRITING_REPAINT_MODE);
        Canvas canvas = surfaceView.getHolder().lockCanvas();
        if (canvas == null) {
            return;
        }
        try {
            RenderingUtils.renderBackground(canvas, viewRect);
            drawRendererContent(bitmap, canvas);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            surfaceView.getHolder().unlockCanvasAndPost(canvas);
            EpdController.resetViewUpdateMode(surfaceView);
        }
    }


    private void drawRendererContent(Bitmap bitmap, Canvas canvas) {
        Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        canvas.drawBitmap(bitmap, rect, rect, null);
    }

}
