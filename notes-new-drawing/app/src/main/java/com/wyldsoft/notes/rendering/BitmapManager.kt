package com.wyldsoft.notes.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.rx.RxManager
import com.onyx.android.sdk.data.note.TouchPoint
import com.wyldsoft.notes.domain.models.PaperTemplate
import com.wyldsoft.notes.drawing.DrawingManager
import com.wyldsoft.notes.pdf.PdfPageRenderer
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.shapemanagement.SelectionManager
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.utils.createStrokePaint
import com.wyldsoft.notes.utils.notePointsToSurfaceTouchPoints
import com.wyldsoft.notes.viewport.ViewportManager
import androidx.core.graphics.withSave

/**
 * Manages bitmap rendering. The bitmap contains only what is currently visible in the viewport.
 * Shape rendering utilities are delegated to [ShapeRenderer].
 */
class BitmapManager(
    private val surfaceView: SurfaceView,
    private val viewModel: EditorViewModel,
    private val rxManager: RxManager?,
    private val getBitmap: () -> Bitmap?,
    private val getBitmapCanvas: () -> Canvas?
) {
    companion object {
        private const val TAG = "BitmapManager"
        private const val HANDLE_NOTE_PADDING = 80f
        private const val SURFACE_PIXEL_BUFFER = 20f
    }

    private val rendererHelper = RendererHelper()
    private val templateRenderer = PaperTemplateRenderer(viewModel.viewportManager)
    private val shapeRenderer = ShapeRenderer(
        viewportManager = viewModel.viewportManager,
        rendererHelper = rendererHelper,
        getBitmap = getBitmap,
        getBitmapCanvas = getBitmapCanvas,
        renderBitmapToScreen = { caller -> renderBitmapToScreen(caller) }
    )

    private var geometrySnapshotBitmap: Bitmap? = null
    private var pdfPageRenderer: PdfPageRenderer? = null

    /** Call when the note changes to (re)create the PDF page renderer if needed. */
    fun onNoteChanged(pdfPath: String?, screenWidth: Int, pdfPageAspectRatio: Float) {
        pdfPageRenderer?.close()
        pdfPageRenderer = if (pdfPath != null && screenWidth > 0 && pdfPageAspectRatio > 0f) {
            PdfPageRenderer(pdfPath, screenWidth, pdfPageAspectRatio)
        } else null
    }

    private fun drawBackground(canvas: Canvas) {
        val note = viewModel.currentNote.value
        val pdfRenderer = pdfPageRenderer
        if (pdfRenderer != null && note.pdfPageCount > 0) {
            pdfRenderer.drawVisiblePages(
                canvas,
                viewModel.viewportManager,
                viewModel.pageHeight.value,
                note.pdfPageCount
            )
        } else {
            val template = viewModel.paperTemplate.value
            if (template != PaperTemplate.BLANK) {
                templateRenderer.drawTemplate(
                    canvas, template, viewModel.screenWidth.value,
                    viewModel.isPaginationEnabled.value, viewModel.pageHeight.value
                )
            }
        }
    }

    fun recreateBitmapFromShapes(setOfShapes: MutableList<BaseShape>?, dirtyRect: RectF? = null, caller: String = "") {
        Log.d("RefreshDebug", "BitmapManager.recreateBitmapFromShapes called from=$caller dirtyRect=$dirtyRect shapeCount=${setOfShapes?.size}")
        // Filter shapes to only visible layers
        val filteredShapes = setOfShapes?.filter { viewModel.isLayerVisible(it.layer) }?.toMutableList()

        val bitmap = getBitmap() ?: return
        val canvas = getBitmapCanvas() ?: return
        val viewportManager = viewModel.viewportManager
        val screenWidth = bitmap.width
        val screenHeight = bitmap.height

        // Dirty-region path: clip canvas and only redraw shapes intersecting the dirty region.
        if (dirtyRect != null) {
            val tl = viewportManager.noteToSurfaceCoordinates(dirtyRect.left, dirtyRect.top)
            val br = viewportManager.noteToSurfaceCoordinates(dirtyRect.right, dirtyRect.bottom)
            val surfaceRect = RectF(
                minOf(tl.x, br.x) - SURFACE_PIXEL_BUFFER, minOf(tl.y, br.y) - SURFACE_PIXEL_BUFFER,
                maxOf(tl.x, br.x) + SURFACE_PIXEL_BUFFER, maxOf(tl.y, br.y) + SURFACE_PIXEL_BUFFER
            )
            surfaceRect.intersect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
            if (surfaceRect.isEmpty) return
            canvas.save()
            canvas.clipRect(surfaceRect)
            canvas.drawColor(Color.WHITE)
            drawBackground(canvas)
            val renderContext = rendererHelper.getRenderContext() ?: run { canvas.restore(); return }
            renderContext.bitmap = bitmap
            renderContext.viewportScale = viewportManager.viewportState.value.scale
            filteredShapes?.forEach { shape ->
                shape.updateShapeRect()
                val noteBounds = shape.boundingRect ?: return@forEach
                val sTl = viewportManager.noteToSurfaceCoordinates(noteBounds.left, noteBounds.top)
                val sBr = viewportManager.noteToSurfaceCoordinates(noteBounds.right, noteBounds.bottom)
                val shapeSurface = RectF(minOf(sTl.x, sBr.x), minOf(sTl.y, sBr.y), maxOf(sTl.x, sBr.x), maxOf(sTl.y, sBr.y))
                if (!RectF.intersects(shapeSurface, surfaceRect)) return@forEach
                canvas.withSave {
                    val surfaceTouchPoints = notePointsToSurfaceTouchPoints(shape.touchPointList, viewportManager)
                    val originalTouchPoints = shape.touchPointList
                    shape.touchPointList = surfaceTouchPoints
                    shapeRenderer.initRenderContext(renderContext, this)
                    shape.render(renderContext)
                    shape.touchPointList = originalTouchPoints
                }
            }
            canvas.restore()
            return
        }

        // Full redraw path
        canvas.drawColor(Color.WHITE)
        drawBackground(canvas)
        val renderContext = rendererHelper.getRenderContext() ?: return
        renderContext.bitmap = bitmap
        renderContext.viewportScale = viewportManager.viewportState.value.scale
        filteredShapes?.forEach { shape ->
            if (!isShapeVisible(shape, viewportManager, screenWidth, screenHeight)) return@forEach
            canvas.withSave {
                val surfaceTouchPoints = notePointsToSurfaceTouchPoints(shape.touchPointList, viewportManager)
                val originalTouchPoints = shape.touchPointList
                shape.touchPointList = surfaceTouchPoints
                shapeRenderer.initRenderContext(renderContext, this)
                shape.render(renderContext)
                shape.touchPointList = originalTouchPoints
            }
        }
    }

    private fun isShapeVisible(shape: BaseShape, viewportManager: ViewportManager, screenWidth: Int, screenHeight: Int): Boolean {
        shape.updateShapeRect()
        val boundingRect = shape.boundingRect ?: return false
        val topLeft = viewportManager.noteToSurfaceCoordinates(boundingRect.left, boundingRect.top)
        val bottomRight = viewportManager.noteToSurfaceCoordinates(boundingRect.right, boundingRect.bottom)
        return !(topLeft.x > screenWidth || bottomRight.x < 0 || topLeft.y > screenHeight || bottomRight.y < 0)
    }

    fun beginGeometryDrawing() {
        val bmp = getBitmap() ?: return
        geometrySnapshotBitmap?.recycle()
        geometrySnapshotBitmap = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true)
    }

    fun drawGeometryPreview(notePoints: List<PointF>, penProfile: PenProfile) {
        val snapshot = geometrySnapshotBitmap ?: return
        val canvas = getBitmapCanvas() ?: return
        if (notePoints.size < 2) return
        canvas.drawBitmap(snapshot, 0f, 0f, null)
        val viewportManager = viewModel.viewportManager
        val surfacePoints = notePoints.map { pt -> viewportManager.noteToSurfaceCoordinates(pt.x, pt.y) }
        val paint = createStrokePaint().apply {
            color = penProfile.getColorAsInt()
            strokeWidth = penProfile.strokeWidth
            style = android.graphics.Paint.Style.STROKE
        }
        val path = Path().apply {
            moveTo(surfacePoints[0].x, surfacePoints[0].y)
            for (i in 1 until surfacePoints.size) lineTo(surfacePoints[i].x, surfacePoints[i].y)
        }
        canvas.drawPath(path, paint)
        Log.d("RefreshDebug", "BitmapManager.drawGeometryPreview → renderBitmapToScreen")
        renderBitmapToScreen("drawGeometryPreview")
    }

    fun endGeometryDrawing() {
        geometrySnapshotBitmap?.recycle()
        geometrySnapshotBitmap = null
    }

    internal fun partialRefresh(dirtyRectNote: RectF, shapes: List<BaseShape>, selectionManager: SelectionManager?, caller: String = "") {
        Log.d("RefreshDebug", "BitmapManager.partialRefresh called from=$caller dirtyRect=$dirtyRectNote shapeCount=${shapes.size} hasSelection=${selectionManager != null}")
        val filteredShapesForRefresh = shapes.filter { viewModel.isLayerVisible(it.layer) }
        val bitmap = getBitmap() ?: return
        val canvas = getBitmapCanvas() ?: return
        val viewportManager = viewModel.viewportManager

        val expandedNote = RectF(
            dirtyRectNote.left - HANDLE_NOTE_PADDING, dirtyRectNote.top - HANDLE_NOTE_PADDING,
            dirtyRectNote.right + HANDLE_NOTE_PADDING, dirtyRectNote.bottom + HANDLE_NOTE_PADDING
        )
        val tl = viewportManager.noteToSurfaceCoordinates(expandedNote.left, expandedNote.top)
        val br = viewportManager.noteToSurfaceCoordinates(expandedNote.right, expandedNote.bottom)
        val dirtyRectSurface = RectF(
            minOf(tl.x, br.x) - SURFACE_PIXEL_BUFFER, minOf(tl.y, br.y) - SURFACE_PIXEL_BUFFER,
            maxOf(tl.x, br.x) + SURFACE_PIXEL_BUFFER, maxOf(tl.y, br.y) + SURFACE_PIXEL_BUFFER
        )
        dirtyRectSurface.intersect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        if (dirtyRectSurface.isEmpty) return

        val renderContext = rendererHelper.getRenderContext() ?: return
        renderContext.bitmap = bitmap
        renderContext.viewportScale = viewportManager.viewportState.value.scale

        canvas.save()
        canvas.clipRect(dirtyRectSurface)
        canvas.drawColor(Color.WHITE)
        drawBackground(canvas)

        for (shape in filteredShapesForRefresh) {
            shape.updateShapeRect()
            val noteBounds = shape.boundingRect ?: continue
            val sTl = viewportManager.noteToSurfaceCoordinates(noteBounds.left, noteBounds.top)
            val sBr = viewportManager.noteToSurfaceCoordinates(noteBounds.right, noteBounds.bottom)
            val shapeSurface = RectF(minOf(sTl.x, sBr.x), minOf(sTl.y, sBr.y), maxOf(sTl.x, sBr.x), maxOf(sTl.y, sBr.y))
            if (!RectF.intersects(shapeSurface, dirtyRectSurface)) continue
            canvas.withSave {
                val surfaceTouchPoints = notePointsToSurfaceTouchPoints(shape.touchPointList, viewportManager)
                val originalTouchPoints = shape.touchPointList
                shape.touchPointList = surfaceTouchPoints
                shapeRenderer.initRenderContext(renderContext, this)
                shape.render(renderContext)
                shape.touchPointList = originalTouchPoints
            }
        }

        selectionManager?.selectionBoundingBox?.let { box ->
            SelectionRenderer.drawBoundingBox(canvas, box, viewportManager)
            selectionManager.getHandlePositions()?.let { SelectionRenderer.drawHandles(canvas, it, viewportManager) }
        }

        canvas.restore()
        renderBitmapRegionToScreen(dirtyRectSurface)
    }

    /**
     * Blits only the specified region of the bitmap to the SurfaceView.
     * More efficient than a full-screen blit for partial updates on e-ink.
     */
    private fun renderBitmapRegionToScreen(surfaceRect: RectF, caller: String = "partialRefresh") {
        Log.d("RefreshDebug", "BitmapManager.renderBitmapRegionToScreen called from=$caller rect=$surfaceRect")
        val bmp = getBitmap() ?: return
        val intRect = Rect(
            surfaceRect.left.toInt(), surfaceRect.top.toInt(),
            surfaceRect.right.toInt(), surfaceRect.bottom.toInt()
        )
        RenderingUtils.enableScreenPost(surfaceView)
        val holder = surfaceView.holder
        val screenCanvas = holder.lockCanvas(intRect) ?: return
        try {
            screenCanvas.clipRect(intRect)
            screenCanvas.drawBitmap(bmp, 0f, 0f, null)
            // Draw page separators if pagination is enabled
            viewModel.let { vm ->
                if (vm.isPaginationEnabled.value) {
                    DrawingManager.drawPageSeparators(
                        canvas = screenCanvas,
                        screenWidth = vm.screenWidth.value,
                        pageHeight = vm.pageHeight.value,
                        isPaginationEnabled = true,
                        viewportManager = vm.viewportManager
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            holder.unlockCanvasAndPost(screenCanvas)
        }
    }

    fun clearDrawing() { Log.d("RefreshDebug", "BitmapManager.clearDrawing → renderBitmapToScreen"); getBitmapCanvas()?.drawColor(Color.WHITE); renderBitmapToScreen("clearDrawing") }

    internal fun renderBitmapToScreen(caller: String = "") {
        Log.d("RefreshDebug", "BitmapManager.renderBitmapToScreen called from=$caller")
        val bitmap = getBitmap() ?: return
        val request = PaginationRendererToScreenRequest(surfaceView, bitmap, viewModel)
        if (rxManager != null) {
            rxManager.enqueue(request, null)
        } else {
            try { request.execute() } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Delegated to ShapeRenderer
    fun renderShapeToBitmap(shape: BaseShape) = shapeRenderer.renderShapeToBitmap(shape)
    fun drawSelectionOverlay(selectionManager: SelectionManager, viewportManager: ViewportManager) =
        shapeRenderer.drawSelectionOverlay(selectionManager)
    fun drawSegmentsToScreen(points: List<TouchPoint>, startIdx: Int, penProfile: PenProfile) =
        shapeRenderer.drawSegmentsToScreen(points, startIdx, penProfile)
}
