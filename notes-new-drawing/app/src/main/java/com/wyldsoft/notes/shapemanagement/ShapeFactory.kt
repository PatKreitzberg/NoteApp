package com.wyldsoft.notes.shapemanagement

import android.util.Log
import com.onyx.android.sdk.data.note.PenTexture
import com.onyx.android.sdk.pen.NeoPenConfigWrapper
import com.onyx.android.sdk.pen.TouchHelper
import com.wyldsoft.notes.shapemanagement.shapes.BrushScribbleShape
import com.wyldsoft.notes.shapemanagement.shapes.CharcoalScribbleShape
import com.wyldsoft.notes.shapemanagement.shapes.MarkerScribbleShape
import com.wyldsoft.notes.shapemanagement.shapes.NewBrushScribbleShape
import com.wyldsoft.notes.shapemanagement.shapes.NormalPencilShape
import com.wyldsoft.notes.shapemanagement.shapes.Shape

/**
 * Factory that creates the correct Shape subclass for a given pen type constant.
 * Maps integer shape-type constants to concrete classes:
 *   SHAPE_PENCIL_SCRIBBLE  -> NormalPencilShape
 *   SHAPE_BRUSH_SCRIBBLE   -> BrushScribbleShape
 *   SHAPE_MARKER_SCRIBBLE  -> MarkerScribbleShape
 *   SHAPE_NEO_BRUSH_SCRIBBLE -> NewBrushScribbleShape
 *   SHAPE_CHARCOAL_SCRIBBLE  -> CharcoalScribbleShape
 *
 * Also provides getStrokeStyle() to map shape types to Onyx TouchHelper
 * stroke style constants, and getCharcoalPenType() for charcoal texture variants.
 * Called by OnyxDrawingActivity.createShapeFromPenType().
 */
object ShapeFactory {
    val TAG = "ShapeFactory"
    const val SHAPE_PENCIL_SCRIBBLE: Int = 0
    const val SHAPE_BRUSH_SCRIBBLE: Int = 1
    const val SHAPE_MARKER_SCRIBBLE: Int = 2
    const val SHAPE_NEO_BRUSH_SCRIBBLE: Int = 3
    const val SHAPE_CHARCOAL_SCRIBBLE: Int = 4

    const val ERASER_STROKE: Int = 0

    fun getStrokeStyle(shapeType: Int, penTexture: Int): Int {
        when (shapeType) {
            SHAPE_BRUSH_SCRIBBLE -> return TouchHelper.STROKE_STYLE_FOUNTAIN
            SHAPE_NEO_BRUSH_SCRIBBLE -> return TouchHelper.STROKE_STYLE_NEO_BRUSH
            SHAPE_PENCIL_SCRIBBLE -> return TouchHelper.STROKE_STYLE_PENCIL
            SHAPE_MARKER_SCRIBBLE -> return TouchHelper.STROKE_STYLE_MARKER
            SHAPE_CHARCOAL_SCRIBBLE -> {
                if (penTexture == PenTexture.CHARCOAL_SHAPE_V2) {
                    return TouchHelper.STROKE_STYLE_CHARCOAL_V2
                }
                return TouchHelper.STROKE_STYLE_CHARCOAL
            }

            else -> return TouchHelper.STROKE_STYLE_PENCIL
        }
    }

    fun createShape(type: Int): Shape {
        Log.d(TAG, "createShape")
        val shape: Shape
        when (type) {
            SHAPE_PENCIL_SCRIBBLE -> shape = NormalPencilShape()
            SHAPE_BRUSH_SCRIBBLE -> shape = BrushScribbleShape()
            SHAPE_MARKER_SCRIBBLE -> shape = MarkerScribbleShape()
            SHAPE_NEO_BRUSH_SCRIBBLE -> shape = NewBrushScribbleShape()
            SHAPE_CHARCOAL_SCRIBBLE -> shape = CharcoalScribbleShape()
            else -> shape = NormalPencilShape()
        }
        return shape
    }

    fun getCharcoalPenType(texture: Int): Int {
        Log.d(TAG, "getCharcoalPenType")
        if (texture == PenTexture.CHARCOAL_SHAPE_V2) {
            return NeoPenConfigWrapper.NEOPEN_PEN_TYPE_CHARCOAL_V2
        }
        return NeoPenConfigWrapper.NEOPEN_PEN_TYPE_CHARCOAL
    }
}
