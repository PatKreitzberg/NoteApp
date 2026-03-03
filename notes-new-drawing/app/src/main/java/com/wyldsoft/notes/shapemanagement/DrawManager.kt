package com.wyldsoft.notes.shapemanagement

import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.rendering.BitmapManager
import android.graphics.PointF
import android.util.Log

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
) {
    private var currentPenProfile: PenProfile = PenProfile.getDefaultProfile(PenType.BALLPEN)

    fun updatePenProfile(penProfile: PenProfile) {
        currentPenProfile = penProfile
    }

    fun newShape(noteCoordinateTouchPointList: TouchPointList): BaseShape {
        Log.d("DrawManager", "Creating new shape with ${noteCoordinateTouchPointList.size()} points")
        // Create shape and add to bitmap then render to screen
        val shape = createShapeFromPenType(noteCoordinateTouchPointList)
        // Convert TouchPointList to List<PointF> for ViewModel (in NoteCoordinates)
        val pointFs = mutableListOf<PointF>()
        val pressures = mutableListOf<Float>()
        val timestamps = mutableListOf<Long>()
        for (i in 0 until noteCoordinateTouchPointList.size()) {
            val tp = noteCoordinateTouchPointList.get(i)
            pointFs.add(PointF(tp.x, tp.y))
            pressures.add(tp.pressure)
            timestamps.add(tp.timestamp)
        }
        onShapeCompleted(shape.id, pointFs, pressures, timestamps)

        // Render the new shape to the bitmap
        bitmapManager.renderShapeToBitmap(shape)
        bitmapManager.renderBitmapToScreen()

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

        // Update bounding rect for hit testing
        shape.updateShapeRect()

        ShapesManager.applyCharcoalTexture(shape, currentPenProfile.penType)

        return shape
    }
}