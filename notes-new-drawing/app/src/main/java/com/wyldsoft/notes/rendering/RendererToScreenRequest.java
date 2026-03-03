package com.wyldsoft.notes.rendering;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.SurfaceView;

import com.onyx.android.sdk.api.device.epd.EpdController;
import com.onyx.android.sdk.api.device.epd.UpdateMode;
import com.onyx.android.sdk.rx.RxRequest;
import com.wyldsoft.notes.sdkintegration.DeviceHelper;

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
        if (DeviceHelper.INSTANCE.isOnyxDevice()) {
            EpdController.setViewDefaultUpdateMode(surfaceView, UpdateMode.HAND_WRITING_REPAINT_MODE);
        }
        Canvas canvas = surfaceView.getHolder().lockCanvas();
        if (canvas == null) {
            return;
        }
        try {
            RenderingUtils.renderBackground(canvas, viewRect);
            RenderingUtils.drawRendererContent(bitmap, canvas);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            surfaceView.getHolder().unlockCanvasAndPost(canvas);
            if (DeviceHelper.INSTANCE.isOnyxDevice()) {
                EpdController.resetViewUpdateMode(surfaceView);
            }
        }
    }

}
