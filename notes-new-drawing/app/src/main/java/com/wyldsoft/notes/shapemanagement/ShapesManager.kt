package com.wyldsoft.notes.shapemanagement

import android.util.Log
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.data.note.TouchPoint

/*
  Manages shapes.
  Lifetime is same as its note repository?
 */

class ShapesManager(
    private val editorViewModel: EditorViewModel
) {
    companion object { val TAG = "ShapeManager"}
    private val shapes: MutableList<BaseShape> = mutableListOf<BaseShape>()

    init {
        // load shapes from current note
        val note = editorViewModel.currentNote.value
        Log.d(TAG, "Initializing ShapeManager with note: ${note.id}, shapes count: ${note.shapes.size}")
        for (domainShape in note.shapes) {
            val sdkShape = convertDomainShapeToSdkShape(domainShape)
            sdkShape.let { shapes.add(it) }
        }
        Log.d(TAG, "ShapeManager initialized with ${shapes.size} shapes from current note")
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

    fun addShape(shape: BaseShape) {
        /*
        Add shape to this.shapes
        Add shape to bitmap
        Add shape to note repository
         */
        shapes.add(shape)
    }

    fun removeShape(shape: BaseShape) {
        /*
        Remove shape from this.shapes
        Remove shape from bitmap
        Remove shape from note repository
         */
        shapes.remove(shape)
    }

    fun translateShape(shape: BaseShape, deltaX: Float, deltaY: Float) {
        //shape.translate(deltaX, deltaY)
    }

    fun scaleShape(shape: BaseShape) {

    }

    fun shapes() : MutableList<BaseShape> {
        return shapes
    }

    fun removeAll(intersectingShapes : Set<BaseShape>) {
        shapes.removeAll(intersectingShapes)
    }

    fun clear() {
        shapes.clear()
    }
}