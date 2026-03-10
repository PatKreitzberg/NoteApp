package com.wyldsoft.notes.rendering;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.SurfaceView;

import com.wyldsoft.notes.EraseArgs;
import com.wyldsoft.notes.InteractiveMode;
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RendererHelper {
    private Map<InteractiveMode, Renderer> rendererMap;
    private RenderContext renderContext;

    public class RenderContext {
        public Paint paint = new Paint();
        public Bitmap bitmap;
        public Canvas canvas;
        public EraseArgs eraseArgs;
        public RectF clipRect;
        public Point viewPoint;
        public float viewportScale = 1.0f;

        public void recycleBitmap() {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            bitmap = null;
        }
    }

    public RendererHelper() {
        initRendererMap();
    }

    private void initRendererMap() {
        rendererMap = new HashMap<>();
        rendererMap.put(InteractiveMode.SCRIBBLE, new NormalRenderer());
        rendererMap.put(InteractiveMode.SCRIBBLE_ERASE, new EraseRenderer());
        rendererMap.put(InteractiveMode.SCRIBBLE_PARTIAL_REFRESH, new PartialRefreshRenderer());
    }

    public Map<InteractiveMode, Renderer> getRendererMap() {
        return rendererMap;
    }

    public RenderContext getRenderContext() {
        if (renderContext == null) {
            renderContext = new RenderContext();
        }
        return renderContext;
    }

    public Renderer getRenderer(InteractiveMode scribbleMode) {
        return getRendererMap().get(scribbleMode);
    }

    public void renderToScreen(InteractiveMode scribbleMode, SurfaceView surfaceView, Bitmap bitmap) {
        getRenderer(scribbleMode).renderToScreen(surfaceView, bitmap);
    }

    public void renderToScreen(InteractiveMode scribbleMode, SurfaceView surfaceView, RenderContext renderContext) {
        getRenderer(scribbleMode).renderToScreen(surfaceView, renderContext);
    }

    public void renderToBitmap(InteractiveMode scribbleMode, SurfaceView surfaceView, RenderContext renderContext) {
        getRenderer(scribbleMode).renderToBitmap(surfaceView, renderContext);
    }

    public void renderToBitmap(InteractiveMode scribbleMode, List<BaseShape> shapes) {
        getRenderer(scribbleMode).renderToBitmap(shapes, getRenderContext());
    }

}