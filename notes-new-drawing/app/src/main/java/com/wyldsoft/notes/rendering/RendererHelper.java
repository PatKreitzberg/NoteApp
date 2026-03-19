package com.wyldsoft.notes.rendering;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;

/**
 * Holds the shared RenderContext used by shape rendering.
 */
public class RendererHelper {
    private RenderContext renderContext;

    public class RenderContext {
        public Paint paint = new Paint();
        public Bitmap bitmap;
        public Canvas canvas;
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

    public RenderContext getRenderContext() {
        if (renderContext == null) {
            renderContext = new RenderContext();
        }
        return renderContext;
    }

}
