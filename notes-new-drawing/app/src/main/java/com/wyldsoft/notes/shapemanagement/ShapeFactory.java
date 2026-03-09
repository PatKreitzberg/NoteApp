package com.wyldsoft.notes.shapemanagement;

import android.util.Log;

import com.wyldsoft.notes.shapemanagement.shapes.BrushScribbleShape;
import com.wyldsoft.notes.shapemanagement.shapes.CharcoalScribbleShape;
import com.wyldsoft.notes.shapemanagement.shapes.MarkerScribbleShape;
import com.wyldsoft.notes.shapemanagement.shapes.NewBrushScribbleShape;
import com.wyldsoft.notes.shapemanagement.shapes.NormalPencilShape;
import com.wyldsoft.notes.shapemanagement.shapes.TextShape;
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape;
import com.onyx.android.sdk.data.note.PenTexture;
import com.onyx.android.sdk.pen.NeoPenConfig;
import com.onyx.android.sdk.pen.TouchHelper;

public class ShapeFactory {
    public static final int SHAPE_PENCIL_SCRIBBLE = 0;
    public static final int SHAPE_BRUSH_SCRIBBLE = 1;
    public static final int SHAPE_MARKER_SCRIBBLE = 2;
    public static final int SHAPE_NEO_BRUSH_SCRIBBLE = 3;
    public static final int SHAPE_CHARCOAL_SCRIBBLE = 4;
    public static final int SHAPE_TEXT = 5;

    static public final int ERASER_STROKE = 0;

    public static int getStrokeStyle(int shapeType, int penTexture) {
        switch (shapeType) {
            case SHAPE_BRUSH_SCRIBBLE:
                return TouchHelper.STROKE_STYLE_FOUNTAIN;
            case SHAPE_NEO_BRUSH_SCRIBBLE:
                return TouchHelper.STROKE_STYLE_NEO_BRUSH;
            case SHAPE_PENCIL_SCRIBBLE:
                return TouchHelper.STROKE_STYLE_PENCIL;
            case SHAPE_MARKER_SCRIBBLE:
                return TouchHelper.STROKE_STYLE_MARKER;
            case SHAPE_CHARCOAL_SCRIBBLE: {
                if (penTexture == PenTexture.CHARCOAL_SHAPE_V2) {
                    return TouchHelper.STROKE_STYLE_CHARCOAL_V2;
                }
                return TouchHelper.STROKE_STYLE_CHARCOAL;
            }
            default:
                return TouchHelper.STROKE_STYLE_PENCIL;
        }
    }

    public static BaseShape createShape(int type) {
        BaseShape shape;
        switch (type) {
            case SHAPE_PENCIL_SCRIBBLE:
                shape = new NormalPencilShape();
                break;
            case SHAPE_BRUSH_SCRIBBLE:
                shape = new BrushScribbleShape();
                break;
            case SHAPE_MARKER_SCRIBBLE:
                shape = new MarkerScribbleShape();
                break;
            case SHAPE_NEO_BRUSH_SCRIBBLE:
                shape = new NewBrushScribbleShape();
                break;
            case SHAPE_CHARCOAL_SCRIBBLE:
                shape = new CharcoalScribbleShape();
                break;
            case SHAPE_TEXT:
                shape = new TextShape();
                break;
            default:
                shape = new NormalPencilShape();
        }
        return shape;
    }

    public static boolean isMarkerShape(int shapeType) {
        return shapeType == SHAPE_MARKER_SCRIBBLE;
    }

    public static int getCharcoalPenType(int texture) {
        Log.d("ShapeFactory", "getCharcoalPenType");
        if (texture == PenTexture.CHARCOAL_SHAPE_V2) {
            return NeoPenConfig.NEOPEN_PEN_TYPE_CHARCOAL_V2;
        }
        return NeoPenConfig.NEOPEN_PEN_TYPE_CHARCOAL;
    }


}
