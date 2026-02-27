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
import com.wyldsoft.notes.shapemanagement.EraseManager
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.DrawManager
import com.wyldsoft.notes.shapemanagement.ShapesManager

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
    private val shapesManager: ShapesManager,
    private val onDrawingStateChanged: (isDrawing: Boolean) -> Unit, // if false then enableFingerTouch and force screen refresh. If true then disableFingerTouch
    private val onShapeCompleted: (id: String, points: List<PointF>, pressures: List<Float>) -> Unit, //
    private val onShapeRemoved: (shapeId: String) -> Unit
) {
    companion object {
        private const val TAG = "OnyxStylusHandler"
    }

    init {
        Log.d(TAG, "NEW OnyxStylusHandler")
    }

    private var drawManager = DrawManager(bitmapManager, onShapeCompleted)

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
        drawManager.updatePenProfile(penProfile)
    }

    /**
     * Creates the Onyx callback for handling stylus input.
     * The callback interfaces with the Onyx SDK to receive raw input events from the stylus.
     */
    fun createOnyxCallback(): RawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            isDrawingInProgress = true
            onDrawingStateChanged(true)
            viewModel.startDrawing()
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            isDrawingInProgress = false
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            // Handle move events if needed
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList?) {
            touchPointList?.points?.let { points ->
                val notePointList = convertTouchPointListToNoteCoordinates(touchPointList)
                val newShape = drawManager.newShape(notePointList)
                shapesManager.addShape(newShape)
            }
            // moved from onEndRawDraing
            isDrawingInProgress = false
            onDrawingStateChanged(false)
            viewModel.endDrawing()
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
                eraseManager.handleErasing(noteErasePointList, shapesManager)
            }
        }
    }


    /**
     * Converts a TouchPointList from SurfaceViewCoordinates to NoteCoordinates
     * using the current ViewportManager transformation.
     */
    private fun convertTouchPointListToNoteCoordinates(surfacePointList: TouchPointList): TouchPointList {
        val notePointList = TouchPointList()
        val viewportManager = viewModel.viewportManager
        
        for (i in 0 until surfacePointList.size()) {
            val tp = surfacePointList.get(i)
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
        }
        
        return notePointList
    }


    /**
     * Clears all drawings
     */
    fun clearDrawing() {
        shapesManager.clear()
        bitmapManager.clearDrawing()
    }

}
