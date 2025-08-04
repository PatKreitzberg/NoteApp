package com.wyldsoft.notes.sdkintegration.onyx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import android.view.SurfaceView
import androidx.core.graphics.createBitmap
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.rendering.RendererHelper
import com.wyldsoft.notes.rendering.RendererToScreenRequest
import com.wyldsoft.notes.shapemanagement.EraseManager
import com.wyldsoft.notes.shapemanagement.ShapeFactory
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.refreshingscreen.PartialEraseRefresh
import com.wyldsoft.notes.pen.PenProfile

/**
 * Handles all stylus-related operations for Onyx devices including drawing and erasing.
 * This class encapsulates the logic for processing stylus input and managing shapes.
 */
class OnyxStylusHandler(
    private val surfaceView: SurfaceView?,
    private val viewModel: EditorViewModel?,
    private val rxManager: RxManager,
    private val onDrawingStateChanged: (isDrawing: Boolean) -> Unit,
    private val onShapeCompleted: (points: List<PointF>, pressures: List<Float>) -> Unit,
    private val onBitmapChanged: () -> Unit,
    private val getBitmap: () -> Bitmap?,
    private val getBitmapCanvas: () -> Canvas?
) {
    companion object {
        private const val TAG = "OnyxStylusHandler"
    }

    // Store all drawn shapes for re-rendering
    val drawnShapes = mutableListOf<BaseShape>()
    
    // Renderer helper for shape rendering
    private val rendererHelper = RendererHelper()
    
    // Erase management
    private val eraseManager = EraseManager()
    private val partialEraseRefresh = PartialEraseRefresh()
    
    // Drawing state
    private var isDrawingInProgress = false
    private var currentPenProfile: PenProfile = PenProfile.getDefaultProfile(PenType.BALLPEN)

    /**
     * Updates the current pen profile used for drawing
     */
    fun updatePenProfile(penProfile: PenProfile) {
        currentPenProfile = penProfile
    }

    /**
     * Creates the Onyx callback for handling stylus input
     */
    fun createOnyxCallback(): RawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            isDrawingInProgress = true
            onDrawingStateChanged(true)
            viewModel?.startDrawing()
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            isDrawingInProgress = false
            onDrawingStateChanged(false)
            onBitmapChanged()
            viewModel?.endDrawing()
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            // Handle move events if needed
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList?) {
            touchPointList?.points?.let { points ->
                if (!isDrawingInProgress) {
                    isDrawingInProgress = true
                }
                drawScribbleToBitmap(points, touchPointList)
            }
        }

        override fun onBeginRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            // Handle erasing start
        }

        override fun onEndRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            // Handle erasing end
        }

        override fun onRawErasingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            // Handle erase move
        }

        override fun onRawErasingTouchPointListReceived(touchPointList: TouchPointList?) {
            touchPointList?.let { erasePointList ->
                handleErasing(erasePointList)
            }
        }
    }

    /**
     * Handles erasing operations
     */
    private fun handleErasing(erasePointList: TouchPointList) {
        Log.d(TAG, "handleErasing called with ${erasePointList.size()} points")
        
        // Convert erase points from SurfaceViewCoordinates to NoteCoordinates
        val noteErasePointList = convertTouchPointListToNoteCoordinates(erasePointList)
        
        // Find shapes that intersect with the erase touch points
        val intersectingShapes = eraseManager.findIntersectingShapes(
            noteErasePointList, 
            drawnShapes
        )
        
        if (intersectingShapes.isNotEmpty()) {
            Log.d(TAG, "Found ${intersectingShapes.size} shapes to erase")
            
            // Calculate refresh area before removing shapes
            val refreshRect = eraseManager.calculateRefreshRect(intersectingShapes)
            
            // Remove intersecting shapes from our shape list
            drawnShapes.removeAll(intersectingShapes.toSet())
            
            // Perform partial refresh of the erased area
            refreshRect?.let { rect: RectF ->
                surfaceView?.let { sv ->
                    partialEraseRefresh.performPartialRefresh(
                        sv,
                        rect,
                        drawnShapes, // Pass remaining shapes
                        rendererHelper,
                        rxManager
                    )
                }
            }
            
            // Also update the main bitmap by recreating it from remaining shapes
            recreateBitmapFromShapes()
        }
    }

    /**
     * Draws scribble to bitmap
     */
    private fun drawScribbleToBitmap(points: List<TouchPoint>, touchPointList: TouchPointList) {
        Log.d(TAG, "drawScribbleToBitmap called list size " + touchPointList.size())
        surfaceView?.let { sv ->
            val bitmap = getBitmap() ?: return

            // Create shape with original touch points (in SurfaceViewCoordinates)
            val shape = createShapeFromPenType(touchPointList)
            
            // Convert touch points to NoteCoordinates for storage
            val notePointList = convertTouchPointListToNoteCoordinates(touchPointList)
            
            // Update shape with note coordinates
            shape.setTouchPointList(notePointList)
            shape.updateShapeRect()
            
            drawnShapes.add(shape)

            // Convert TouchPointList to List<PointF> for ViewModel (in NoteCoordinates)
            val pointFs = mutableListOf<PointF>()
            val pressures = mutableListOf<Float>()
            for (i in 0 until notePointList.size()) {
                val tp = notePointList.get(i)
                pointFs.add(PointF(tp.x, tp.y))
                pressures.add(tp.pressure)
            }
            onShapeCompleted(pointFs, pressures)

            // Render the new shape to the bitmap
            renderShapeToBitmap(shape)
            renderBitmapToScreen(sv, bitmap)
        }
    }

    /**
     * Creates a shape based on the current pen type
     */
    private fun createShapeFromPenType(touchPointList: TouchPointList): BaseShape {
        // Map pen type to shape type
        val shapeType = when (currentPenProfile.penType) {
            PenType.BALLPEN, PenType.PENCIL -> ShapeFactory.SHAPE_PENCIL_SCRIBBLE
            PenType.FOUNTAIN -> ShapeFactory.SHAPE_BRUSH_SCRIBBLE
            PenType.MARKER -> ShapeFactory.SHAPE_MARKER_SCRIBBLE
            PenType.CHARCOAL, PenType.CHARCOAL_V2 -> ShapeFactory.SHAPE_CHARCOAL_SCRIBBLE
            PenType.NEO_BRUSH -> ShapeFactory.SHAPE_NEO_BRUSH_SCRIBBLE
            PenType.DASH -> ShapeFactory.SHAPE_PENCIL_SCRIBBLE // Default to pencil for dash
            else -> ShapeFactory.SHAPE_PENCIL_SCRIBBLE // Default fallback
        }

        // Create the shape
        val shape = ShapeFactory.createShape(shapeType)
        shape.setTouchPointList(touchPointList)
            .setStrokeColor(currentPenProfile.getColorAsInt())
            .setStrokeWidth(currentPenProfile.strokeWidth)
            .setShapeType(shapeType)
            
        // Update bounding rect for hit testing
        shape.updateShapeRect()

        // Set texture for charcoal if needed
        if (currentPenProfile.penType == PenType.CHARCOAL_V2) {
            shape.setTexture(com.onyx.android.sdk.data.note.PenTexture.CHARCOAL_SHAPE_V2)
        } else if (currentPenProfile.penType == PenType.CHARCOAL) {
            shape.setTexture(com.onyx.android.sdk.data.note.PenTexture.CHARCOAL_SHAPE_V1)
        }

        return shape
    }

    /**
     * Renders a shape to the bitmap
     */
    private fun renderShapeToBitmap(shape: BaseShape) {
        val bmp = getBitmap() ?: return
        val renderContext = rendererHelper.getRenderContext() ?: return
        val canvas = getBitmapCanvas() ?: return
            
            // Apply viewport transformation if available
            canvas.save()
            viewModel?.viewportManager?.let { viewportManager ->
                canvas.concat(viewportManager.getTransformMatrix())
            }
            
            renderContext.bitmap = bmp
            renderContext.canvas = canvas
            renderContext.paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            // Initialize viewPoint for shapes that need it (like CharcoalScribbleShape)
            renderContext.viewPoint = android.graphics.Point(0, 0)

            shape.render(renderContext)
            canvas.restore()
        }
    }

    /**
     * Converts a TouchPointList from SurfaceViewCoordinates to NoteCoordinates
     * using the current ViewportManager transformation.
     */
    private fun convertTouchPointListToNoteCoordinates(surfacePointList: TouchPointList): TouchPointList {
        val notePointList = TouchPointList()
        val viewportManager = viewModel?.viewportManager
        
        if (viewportManager == null) {
            Log.w(TAG, "ViewportManager is null in convertTouchPointListToNoteCoordinates")
        }
        
        for (i in 0 until surfacePointList.size()) {
            val tp = surfacePointList.get(i)
            if (viewportManager != null) {
                // Convert from SurfaceViewCoordinates to NoteCoordinates
                val notePoint = viewportManager.surfaceToNoteCoordinates(tp.x, tp.y)
                val noteTouchPoint = TouchPoint(
                    notePoint.x, 
                    notePoint.y, 
                    tp.pressure, 
                    tp.size, 
                    tp.timestamp
                )
                notePointList.add(noteTouchPoint)
            } else {
                // If no viewport manager, use original coordinates
                notePointList.add(tp)
            }
        }
        
        return notePointList
    }

    /**
     * Recreates the bitmap from all stored shapes
     */
    fun recreateBitmapFromShapes() {
        val bitmap = getBitmap() ?: return
        val canvas = getBitmapCanvas() ?: return
        
        // Clear the bitmap
        canvas.drawColor(Color.WHITE)
        
        // Get render context
        val renderContext = rendererHelper.getRenderContext() ?: return
        renderContext.bitmap = bitmap
            
            // Apply viewport transformation if available
            canvas.save()
            viewModel?.viewportManager?.let { viewportManager ->
                canvas.concat(viewportManager.getTransformMatrix())
            }
            
            renderContext.canvas = canvas
            renderContext.paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            // Initialize viewPoint for shapes that need it (like CharcoalScribbleShape)
            renderContext.viewPoint = android.graphics.Point(0, 0)

            // Render all shapes
            for (shape in drawnShapes) {
                shape.render(renderContext)
            }
            
            canvas.restore()
        }
    }


    /**
     * Renders bitmap to screen
     */
    private fun renderBitmapToScreen(surfaceView: SurfaceView, bitmap: Bitmap?) {
        if (bitmap != null) {
            rxManager.enqueue(
                RendererToScreenRequest(
                    surfaceView,
                    bitmap
                ), null)
        }
    }

    /**
     * Clears all drawings
     */
    fun clearDrawing() {
        drawnShapes.clear()
        getBitmapCanvas()?.drawColor(Color.WHITE)
    }

    /**
     * Converts domain model shape to Onyx SDK shape
     */
    fun convertDomainShapeToSdkShape(domainShape: com.wyldsoft.notes.domain.models.Shape): BaseShape {
        // Create TouchPointList from domain shape points
        val touchPointList = TouchPointList()
        for (i in domainShape.points.indices) {
            val point = domainShape.points[i]
            val pressure = if (i < domainShape.pressure.size) domainShape.pressure[i] else 0.5f
            val touchPoint = TouchPoint(point.x, point.y, pressure, 1f, System.currentTimeMillis())
            touchPointList.add(touchPoint)
        }
        
        // Map shape type - for now assuming all are strokes
        val shapeType = ShapeFactory.SHAPE_PENCIL_SCRIBBLE
        
        val shape = ShapeFactory.createShape(shapeType)
        shape.setTouchPointList(touchPointList)
            .setStrokeColor(domainShape.strokeColor)
            .setStrokeWidth(domainShape.strokeWidth)
            .setShapeType(shapeType)
            
        // Update bounding rect for hit testing
        shape.updateShapeRect()
        
        return shape
    }
}