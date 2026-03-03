package com.wyldsoft.notes.rendering;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.Nullable;

import com.wyldsoft.notes.shapemanagement.shapes.BaseShape;

import java.util.List;

public abstract class BaseRenderer implements Renderer {

    @Override
    public void renderToBitmap(SurfaceView surfaceView, RendererHelper.RenderContext renderContext) {
    }

    @Override
    public void renderToBitmap(List<BaseShape> shapes, RendererHelper.RenderContext renderContext) {
    }

    @Override
    public void renderToScreen(SurfaceView surfaceView, Bitmap bitmap) {
    }

    protected void drawRendererContent(Bitmap bitmap, Canvas canvas) {
        Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        canvas.drawBitmap(bitmap, rect, rect, null);
    }

    protected Canvas lockHardwareCanvas(SurfaceHolder holder, @Nullable Rect dirty) {
        return holder.lockCanvas(dirty);
    }

    protected void unlockCanvasAndPost(SurfaceView surfaceView, Canvas canvas) {
        surfaceView.getHolder().unlockCanvasAndPost(canvas);
    }

    protected void beforeUnlockCanvas(SurfaceView surfaceView) {
        RenderingUtils.enableScreenPost(surfaceView);
    }

}
