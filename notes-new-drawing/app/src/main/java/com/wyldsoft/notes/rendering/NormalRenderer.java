package com.wyldsoft.notes.rendering;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.SurfaceView;

import com.wyldsoft.notes.shapemanagement.shapes.BaseShape;

import java.util.List;

public class NormalRenderer extends BaseRenderer {

    @Override
    public void renderToBitmap(List<BaseShape> shapes, RendererHelper.RenderContext renderContext) {
        for (BaseShape shape : shapes) {
            shape.render(renderContext);
        }
    }

    @Override
    public void renderToScreen(SurfaceView surfaceView, Bitmap bitmap) {
        if (surfaceView == null) {
            return;
        }
        Rect rect = RenderingUtils.checkSurfaceView(surfaceView);
        if (rect == null) {
            return;
        }
        Canvas canvas = lockHardwareCanvas(surfaceView.getHolder(), null);
        if (canvas == null) {
            return;
        }
        try {
            RenderingUtils.renderBackground(canvas, rect);
            drawRendererContent(bitmap, canvas);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            beforeUnlockCanvas(surfaceView);
            unlockCanvasAndPost(surfaceView, canvas);
        }
    }

    @Override
    public void renderToScreen(SurfaceView surfaceView, RendererHelper.RenderContext renderContext) {
        renderToScreen(surfaceView, renderContext.bitmap);
    }

}
