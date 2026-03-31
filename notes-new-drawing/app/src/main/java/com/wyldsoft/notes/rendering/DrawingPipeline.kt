package com.wyldsoft.notes.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.data.database.repository.ShapeRepository
import com.wyldsoft.notes.data.mappers.ShapeMapper
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.refreshingscreen.PartialEraseRefresh
import com.wyldsoft.notes.shapemanagement.EraseManager
import com.wyldsoft.notes.shapemanagement.ShapeFactory
import com.wyldsoft.notes.shapemanagement.shapes.Shape
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages the drawing pipeline: shape creation, rendering to bitmap, and erasing.
 * Owns the in-memory list of drawn shapes and coordinates viewport transforms
 * for rendering. Persists shapes to the database when a noteId and repository are set.
 */
class DrawingPipeline(
    private val viewportManager: ViewportManager,
    private val scope: CoroutineScope? = null,
    var shapeRepository: ShapeRepository? = null,
    var noteId: String? = null
) {
    private val TAG = "DrawingPipeline"

    private val drawnShapes = mutableListOf<Shape>()
    private val eraseManager = EraseManager()
    private val partialEraseRefresh = PartialEraseRefresh()
    var paginationManager: PaginationManager? = null

    fun clearShapes() {
        Log.d(TAG, "clearShapes")
        drawnShapes.clear()
    }

    fun drawScribbleToBitmap(
        touchPointList: TouchPointList,
        bitmap: Bitmap,
        penProfile: PenProfile
    ) {
        Log.d(TAG, "drawScribbleToBitmap list size ${touchPointList.size()}")
        val notePointList = viewportManager.viewportToNoteTouchPoints(touchPointList)
        val shape = createShapeFromPenType(notePointList, penProfile)
        drawnShapes.add(shape)
        renderShapeToBitmap(shape, bitmap)
        persistShape(shape)
    }

    private fun persistShape(shape: Shape) {
        val repo = shapeRepository ?: return
        val nId = noteId ?: return
        val entity = ShapeMapper.toEntity(shape, nId)
        scope?.launch(Dispatchers.IO) {
            repo.saveShape(entity)
        }
    }

    private fun deleteErasedShapes(shapes: List<Shape>) {
        val repo = shapeRepository ?: return
        scope?.launch(Dispatchers.IO) {
            for (shape in shapes) {
                shape.entityId?.let { repo.deleteShape(it) }
            }
        }
    }

    suspend fun loadShapes(noteId: String) {
        Log.d(TAG, "loadShapes noteId=$noteId")
        val repo = shapeRepository ?: return
        val entities = repo.getShapesForNote(noteId)
        val shapes = entities.map { ShapeMapper.toShape(it) }
        drawnShapes.clear()
        drawnShapes.addAll(shapes)
        Log.d(TAG, "loadShapes loaded ${shapes.size} shapes")
    }

    fun handleErasing(
        erasePointList: TouchPointList,
        bitmap: Bitmap?,
        surfaceView: SurfaceView,
        rxManager: RxManager
    ): BitmapState? {
        Log.d(TAG, "handleErasing called")
        val noteErasePointList = viewportManager.viewportToNoteTouchPoints(erasePointList)
        val intersectingShapes = eraseManager.findIntersectingShapes(
            noteErasePointList,
            drawnShapes
        )
        if (intersectingShapes.isNotEmpty()) {
            drawnShapes.removeAll(intersectingShapes.toSet())
            deleteErasedShapes(intersectingShapes)

            val newState = recreateBitmapFromShapes(bitmap, surfaceView.width, surfaceView.height)

            val refreshRect = eraseManager.calculateRefreshRect(intersectingShapes)
            if (refreshRect != null) {
                val viewportRect = viewportManager.noteToViewport(refreshRect)
                partialEraseRefresh.performPartialRefresh(
                    surfaceView,
                    viewportRect,
                    drawnShapes.toList(),
                    viewportManager,
                    rxManager
                )
            }
            return newState
        }
        return null
    }

    /**
     * Recreates the full offscreen bitmap by re-rendering all stored shapes.
     * Returns the new bitmap and canvas so the caller can update its fields.
     */
    fun recreateBitmapFromShapes(
        currentBitmap: Bitmap?,
        width: Int,
        height: Int
    ): BitmapState {
        Log.d(TAG, "recreateBitmapFromShapes scale=${viewportManager.scale} scrollX=${viewportManager.scrollX} scrollY=${viewportManager.scrollY}")

        val bmp: Bitmap
        val canvas: Canvas
        if (currentBitmap != null && !currentBitmap.isRecycled
            && currentBitmap.width == width && currentBitmap.height == height
        ) {
            bmp = currentBitmap
            canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
        } else {
            bmp = createBitmap(width, height)
            canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
        }

        val renderContext = RenderContext.createForBitmap(bmp, canvas)

        for (shape in drawnShapes) {
            shape.renderInViewport(renderContext, viewportManager)
        }

        paginationManager?.let { pm ->
            renderPaginationOverlays(canvas, pm, width, height)
        }

        return BitmapState(bmp, canvas)
    }

    private fun renderPaginationOverlays(
        canvas: Canvas,
        pm: PaginationManager,
        screenWidth: Int,
        screenHeight: Int
    ) {
        Log.d(TAG, "renderPaginationOverlays: ${pm.pageCount} pages")
        val gapPaint = Paint().apply {
            color = Color.parseColor("#4A90D9")
            style = Paint.Style.FILL
        }
        val textPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f * viewportManager.scale
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        for (gapRect in pm.allGapRects()) {
            val vpRect = viewportManager.noteToViewport(gapRect)
            if (vpRect.bottom > 0 && vpRect.top < screenHeight) {
                canvas.drawRect(vpRect, gapPaint)
            }
        }

        for (i in 0 until pm.pageCount) {
            val vpX = viewportManager.noteToViewportX(pm.pageWidth - 20f)
            val vpY = viewportManager.noteToViewportY(pm.pageTopY(i) + 30f)
            if (vpY > -50 && vpY < screenHeight + 50) {
                canvas.drawText("${i + 1}", vpX, vpY, textPaint)
            }
        }
    }

    private fun createShapeFromPenType(
        touchPointList: TouchPointList,
        penProfile: PenProfile
    ): Shape {
        Log.d(TAG, "createShapeFromPenType")
        val shapeType = when (penProfile.penType) {
            PenType.BALLPEN, PenType.PENCIL -> ShapeFactory.SHAPE_PENCIL_SCRIBBLE
            PenType.FOUNTAIN -> ShapeFactory.SHAPE_BRUSH_SCRIBBLE
            PenType.MARKER -> ShapeFactory.SHAPE_MARKER_SCRIBBLE
            PenType.CHARCOAL, PenType.CHARCOAL_V2 -> ShapeFactory.SHAPE_CHARCOAL_SCRIBBLE
            PenType.NEO_BRUSH -> ShapeFactory.SHAPE_NEO_BRUSH_SCRIBBLE
            PenType.DASH -> ShapeFactory.SHAPE_DASH_SCRIBBLE
        }

        val shape = ShapeFactory.createShape(shapeType)
        shape.touchPointList = touchPointList
        shape.strokeColor = penProfile.getColorAsInt()
        shape.strokeWidth = penProfile.strokeWidth
        shape.shapeType = shapeType
        shape.penType = penProfile.penType
        shape.updateShapeRect()

        if (penProfile.penType == PenType.CHARCOAL_V2) {
            shape.texture = com.onyx.android.sdk.data.note.PenTexture.CHARCOAL_SHAPE_V2
        } else if (penProfile.penType == PenType.CHARCOAL) {
            shape.texture = com.onyx.android.sdk.data.note.PenTexture.CHARCOAL_SHAPE_V1
        }

        return shape
    }

    private fun renderShapeToBitmap(shape: Shape, bitmap: Bitmap) {
        val renderContext = RenderContext.createForBitmap(bitmap, Canvas(bitmap))
        shape.renderInViewport(renderContext, viewportManager)
    }
}

/**
 * Holds a bitmap and its associated canvas, returned by DrawingPipeline
 * when it creates or updates the offscreen bitmap.
 */
data class BitmapState(val bitmap: Bitmap, val canvas: Canvas)
