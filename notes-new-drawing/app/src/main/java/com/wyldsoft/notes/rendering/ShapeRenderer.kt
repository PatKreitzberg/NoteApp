package com.wyldsoft.notes.rendering

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Bitmap
import com.onyx.android.sdk.data.note.TouchPoint
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.shapemanagement.SelectionManager
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.utils.createStrokePaint
import com.wyldsoft.notes.utils.notePointsToSurfaceTouchPoints
import com.wyldsoft.notes.viewport.ViewportManager
import androidx.core.graphics.withSave

/**
 * Utility rendering operations extracted from BitmapManager to keep it under 300 lines.
 */
class ShapeRenderer(
    private val viewportManager: ViewportManager,
    private val rendererHelper: RendererHelper,
    private val getBitmap: () -> Bitmap?,
    private val getBitmapCanvas: () -> Canvas?,
    private val renderBitmapToScreen: () -> Unit
) {
    fun initRenderContext(renderContext: RendererHelper.RenderContext, canvas: Canvas) {
        renderContext.canvas = canvas
        renderContext.paint = createStrokePaint()
        renderContext.viewPoint = android.graphics.Point(0, 0)
    }

    fun renderShapeToBitmap(shape: BaseShape) {
        val bmp = getBitmap() ?: return
        val renderContext = rendererHelper.getRenderContext() ?: return
        val canvas = getBitmapCanvas() ?: return

        renderContext.bitmap = bmp
        val surfaceTouchPoints = notePointsToSurfaceTouchPoints(shape.touchPointList, viewportManager)
        val originalTouchPoints = shape.touchPointList
        shape.touchPointList = surfaceTouchPoints
        canvas.withSave {
            initRenderContext(renderContext, this)
            shape.render(renderContext)
        }
        shape.touchPointList = originalTouchPoints
    }

    fun drawSelectionOverlay(selectionManager: SelectionManager) {
        val canvas = getBitmapCanvas() ?: return
        if (selectionManager.isLassoInProgress) {
            SelectionRenderer.drawLasso(canvas, selectionManager.getLassoPoints(), viewportManager)
        }
        selectionManager.selectionBoundingBox?.let { box ->
            SelectionRenderer.drawBoundingBox(canvas, box, viewportManager)
            selectionManager.getHandlePositions()?.let { handles ->
                SelectionRenderer.drawHandles(canvas, handles, viewportManager)
            }
        }
        renderBitmapToScreen()
    }

    fun drawSegmentsToScreen(points: List<TouchPoint>, startIdx: Int, penProfile: PenProfile) {
        val canvas = getBitmapCanvas() ?: return
        val paint = createStrokePaint().apply {
            color = penProfile.getColorAsInt()
            strokeWidth = penProfile.strokeWidth
        }
        val maxPressure = 1.0f
        for (i in startIdx until points.size - 1) {
            val p1 = points[i]; val p2 = points[i + 1]
            val pressure = maxOf(p1.pressure, 0.1f)
            paint.strokeWidth = maxOf(penProfile.strokeWidth * (pressure / maxPressure) * 2.5f, 1f)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }
        renderBitmapToScreen()
    }
}
