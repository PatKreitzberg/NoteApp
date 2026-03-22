package com.wyldsoft.notes.shapemanagement

import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.rendering.BitmapManager
import android.graphics.PointF
import android.util.Log
import com.wyldsoft.notes.utils.extractTouchData

/*
Handles when the user creates a new shape by drawing with the stylus.
This class is responsible for:
- Creating a new shape based on the current pen profile
- Rendering the shape to the bitmap
- Notifying the ViewModel when a shape is completed
 */
class DrawManager(
    private var bitmapManager: BitmapManager,
    private val onShapeCompleted: (id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long>) -> Unit,
    private val getActiveLayer: () -> Int = { 1 }
) {
    private var currentPenProfile: PenProfile = PenProfile.getDefaultProfile(PenType.BALLPEN)

    fun updatePenProfile(penProfile: PenProfile) {
        currentPenProfile = penProfile
    }

    fun newShape(noteCoordinateTouchPointList: TouchPointList): BaseShape {
        Log.d("DROPSTROKEBUG", "DrawManager.newShape: ${noteCoordinateTouchPointList.size()} points")
        // Create shape and add to bitmap then render to screen
        val shape = createShapeFromPenType(noteCoordinateTouchPointList)
        Log.d("DROPSTROKEBUG", "DrawManager.newShape: shape created id=${shape.id}, calling onShapeCompleted")
        // Convert TouchPointList to domain data for ViewModel (in NoteCoordinates)
        val touchData = extractTouchData(noteCoordinateTouchPointList)
        onShapeCompleted(shape.id, touchData.points, touchData.pressures, touchData.timestamps)
        Log.d("DROPSTROKEBUG", "DrawManager.newShape: onShapeCompleted fired, rendering to bitmap")

        // Render the new shape to the bitmap
        bitmapManager.renderShapeToBitmap(shape)
        Log.d("RefreshDebug", "DrawManager.newShape → renderBitmapToScreen for shape id=${shape.id}")

        // As of note there is no need to render bitmap to screen when adding new shape
        bitmapManager.renderBitmapToScreen("DrawManager.newShape")
        Log.d("DROPSTROKEBUG", "DrawManager.newShape: rendered to bitmap and screen, returning shape id=${shape.id}")

        // return to OnyxStylusHandler so it can put in drawnShapes
        return shape
    }

    /**
     * Creates a shape based on the current pen type
     */
    private fun createShapeFromPenType(touchPointList: TouchPointList): BaseShape {
        val shapeType = ShapesManager.penTypeToShapeType(currentPenProfile.penType)

        // Create the shape
        val shape = ShapeFactory.createShape(shapeType)
        shape.setTouchPointList(touchPointList)
            .setStrokeColor(currentPenProfile.getColorAsInt())
            .setStrokeWidth(currentPenProfile.strokeWidth)
            .setShapeType(shapeType)

        // Set layer from current active layer
        shape.layer = getActiveLayer()

        // Update bounding rect for hit testing
        shape.updateShapeRect()

        ShapesManager.applyCharcoalTexture(shape, currentPenProfile.penType)

        return shape
    }
}