package com.wyldsoft.notes.shapemanagement

import android.util.Log
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.shapemanagement.shapes.TextShape
import com.wyldsoft.notes.utils.domainPointsToTouchPointList

/*
  Manages shapes.
  Lifetime is same as its note repository?
 */

class ShapesManager(
    private val editorViewModel: EditorViewModel
) {
    companion object {
        val TAG = "ShapeManager"

        fun penTypeToShapeType(penType: PenType): Int {
            return when (penType) {
                PenType.BALLPEN, PenType.PENCIL, PenType.DASH -> ShapeFactory.SHAPE_PENCIL_SCRIBBLE
                PenType.FOUNTAIN -> ShapeFactory.SHAPE_BRUSH_SCRIBBLE
                PenType.MARKER -> ShapeFactory.SHAPE_MARKER_SCRIBBLE
                PenType.CHARCOAL, PenType.CHARCOAL_V2 -> ShapeFactory.SHAPE_CHARCOAL_SCRIBBLE
                PenType.NEO_BRUSH -> ShapeFactory.SHAPE_NEO_BRUSH_SCRIBBLE
            }
        }

        fun applyCharcoalTexture(shape: BaseShape, penType: PenType) {
            if (penType == PenType.CHARCOAL_V2) {
                shape.setTexture(com.onyx.android.sdk.data.note.PenTexture.CHARCOAL_SHAPE_V2)
            } else if (penType == PenType.CHARCOAL) {
                shape.setTexture(com.onyx.android.sdk.data.note.PenTexture.CHARCOAL_SHAPE_V1)
            }
        }
    }
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
        val touchPointList = domainPointsToTouchPointList(domainShape.points, domainShape.pressure)

        if (domainShape.type == ShapeType.TEXT) {
            val textShape = TextShape()
            textShape.setId(domainShape.id)
            textShape.setText(domainShape.text ?: "")
            textShape.setFontSize(domainShape.fontSize)
            textShape.setFontFamily(domainShape.fontFamily)
            textShape.setTouchPointList(touchPointList)
                .setStrokeColor(domainShape.strokeColor)
                .setStrokeWidth(domainShape.strokeWidth)
                .setShapeType(ShapeFactory.SHAPE_TEXT)
            textShape.updateShapeRect()
            return textShape
        }

        // Map pen type to SDK shape type
        val shapeType = penTypeToShapeType(domainShape.penType)

        val shape = ShapeFactory.createShape(shapeType)
        shape.setId(domainShape.id)
        shape.setTouchPointList(touchPointList)
            .setStrokeColor(domainShape.strokeColor)
            .setStrokeWidth(domainShape.strokeWidth)
            .setShapeType(shapeType)

        applyCharcoalTexture(shape, domainShape.penType)

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

    fun shapes() : MutableList<BaseShape> {
        return shapes
    }

    fun removeAll(intersectingShapes : Set<BaseShape>) {
        shapes.removeAll(intersectingShapes)
    }

    fun clear() {
        shapes.clear()
    }

    fun findShapeById(id: String): BaseShape? {
        return shapes.find { it.id == id }
    }

    /**
     * Returns the maximum Y coordinate across all shapes (bottom of lowest shape).
     * Returns 0f if there are no shapes.
     */
    fun getContentMaxY(): Float {
        var maxY = 0f
        for (shape in shapes) {
            shape.updateShapeRect()
            val rect = shape.boundingRect ?: continue
            if (rect.bottom > maxY) {
                maxY = rect.bottom
            }
        }
        return maxY
    }
}