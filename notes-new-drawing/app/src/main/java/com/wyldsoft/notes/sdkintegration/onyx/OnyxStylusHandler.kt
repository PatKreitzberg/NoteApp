package com.wyldsoft.notes.sdkintegration.onyx

import android.graphics.PointF
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.viewport.ViewportManager
import com.wyldsoft.notes.shapemanagement.EraseManager
import com.wyldsoft.notes.shapemanagement.ShapeFactory
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.rendering.BitmapManager

/**
 * Handles all stylus-related operations for Onyx devices:
 * Drawing:
 *   - Starts and ends drawing sessions
 *   - Processes touch points to create shapes
 *   - Manages scribble rendering to bitmap
 * Erasing:
 *   - Starts and ends erasing sessions
 *   - Processes touch points to find and erase shapes
 *   - Manages partial refresh of erased areas
 * Shape Management:
 *   - Stores drawn shapes
 *   - Converts touch points to NoteCoordinates
 *   - Renders shapes to bitmap
 * This class encapsulates the logic for processing stylus input and managing shapes.
 */
class OnyxStylusHandler(
    protected var surfaceView: SurfaceView,
    private val viewModel: EditorViewModel,
    private val rxManager: RxManager,
    private val bitmapManager: BitmapManager,
    private val onDrawingStateChanged: (isDrawing: Boolean) -> Unit,
    private val onShapeCompleted: (id: String, points: List<PointF>, pressures: List<Float>) -> Unit,
    private val onShapeRemoved: (shapeId: String) -> Unit
) {
    companion object {
        private const val TAG = "OnyxStylusHandler"
    }

    init {
        Log.d(TAG, "NEW OnyxStylusHandler")
    }

    // Store all drawn shapes for re-renderings
    val drawnShapes = mutableListOf<BaseShape>()

    // Erase management
    private val eraseManager = EraseManager(surfaceView, rxManager, bitmapManager, onShapeRemoved)

    // Drawing state
    private var isDrawingInProgress = false
    private var isErasingInProgress = false
    private var currentPenProfile: PenProfile = PenProfile.getDefaultProfile(PenType.BALLPEN)

    /**
     * Returns true if erasing is currently in progress
     */
    fun isErasing(): Boolean = isErasingInProgress
    
    /**
     * Updates the current pen profile used for drawing
     */
    fun updatePenProfile(penProfile: PenProfile) {
        currentPenProfile = penProfile
    }

    /**
     * Creates the Onyx callback for handling stylus input.
     * The callback interfaces with the Onyx SDK to receive raw input events from the stylus.
     */
    fun createOnyxCallback(): RawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            isDrawingInProgress = true
            onDrawingStateChanged(true)
            viewModel?.startDrawing()
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            isDrawingInProgress = false
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            // Handle move events if needed
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList?) {
            touchPointList?.points?.let { points ->
                if (!isDrawingInProgress) {
                    isDrawingInProgress = true
                }
                drawScribbleToBitmap(touchPointList)
            }

            // moved from onEndRawDraing
            isDrawingInProgress = false
            onDrawingStateChanged(false)
            viewModel?.endDrawing()
        }

        override fun onBeginRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            // Handle erasing start
            isErasingInProgress = true
            Log.d(TAG, "Erasing started")
        }

        override fun onEndRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            // Handle erasing end
            isErasingInProgress = false
            Log.d(TAG, "Erasing ended")
        }

        override fun onRawErasingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            // Handle erase move
        }

        override fun onRawErasingTouchPointListReceived(touchPointList: TouchPointList?) {
            touchPointList?.let { erasePointList ->
                val noteErasePointList = convertTouchPointListToNoteCoordinates(erasePointList)
                eraseManager.handleErasing(noteErasePointList, drawnShapes)
            }
        }
    }

    /**
     * Draws scribble to bitmap
     */
    private fun drawScribbleToBitmap(touchPointList: TouchPointList) {
        Log.d("DebugAug11.1", "drawScribbleToBitmap called list size " + touchPointList.size())
        surfaceView.let { sv: SurfaceView ->
            // Create shape with original touch points (in SurfaceViewCoordinates)
            val shape = createShapeFromPenType(touchPointList)

            // Convert touch points to NoteCoordinates for storage
            val notePointList = convertTouchPointListToNoteCoordinates(touchPointList)

            // Update shape with note coordinates
            shape.setTouchPointList(notePointList)
            shape.updateShapeRect()

            // add shape to drawnShapes
            drawnShapes.add(shape)

            // Convert TouchPointList to List<PointF> for ViewModel (in NoteCoordinates)
            val pointFs = mutableListOf<PointF>()
            val pressures = mutableListOf<Float>()
            for (i in 0 until notePointList.size()) {
                val tp = notePointList.get(i)
                pointFs.add(PointF(tp.x, tp.y))
                pressures.add(tp.pressure)
            }
            onShapeCompleted(shape.id, pointFs, pressures)

            // Render the new shape to the bitmap
            bitmapManager.renderShapeToBitmap(shape)
            bitmapManager.renderBitmapToScreen()
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
     * Converts a TouchPointList from SurfaceViewCoordinates to NoteCoordinates
     * using the current ViewportManager transformation.
     */
    private fun convertTouchPointListToNoteCoordinates(surfacePointList: TouchPointList): TouchPointList {
        val notePointList = TouchPointList()
        val viewportManager = viewModel.viewportManager
        
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
     * Clears all drawings
     */
    fun clearDrawing() {
        drawnShapes.clear()
        bitmapManager.clearDrawing()
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
