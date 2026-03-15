package com.wyldsoft.notes.domain.models

import android.graphics.PointF
import com.wyldsoft.notes.pen.PenType
import java.util.UUID

/**
 * Represents a shape drawn on a note.
 * USED FOR INTERACTING WITH THE DATABASE I BELIEVE
 * @property id Unique identifier for the shape.
 * @property type Type of the shape (e.g., stroke, rectangle, circle).
 * @property points List of points defining the shape's geometry.
 * @property strokeWidth Width of the stroke used to draw the shape.
 * @property strokeColor Color of the stroke used to draw the shape.
 * @property penType The pen type used to draw this shape (for correct rendering style on redraw).
 * @property pressure Pressure values for each point, if applicable.
 * @property timestamp Timestamp when the shape was created or modified.
 */
data class Shape(
    val id: String = UUID.randomUUID().toString(),
    val type: ShapeType = ShapeType.STROKE,
    val points: List<PointF>, // saved in NoteCoordinates
    val strokeWidth: Float,
    val strokeColor: Int,
    val penType: PenType = PenType.BALLPEN,
    val pressure: List<Float> = emptyList(),
    val pointTimestamps: List<Long> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val text: String? = null,
    val fontSize: Float = 32f,
    val fontFamily: String = "sans-serif",
    val layer: Int = 1
)

enum class ShapeType {
    STROKE,
    RECTANGLE,
    CIRCLE,
    TRIANGLE,
    LINE,
    TEXT
}