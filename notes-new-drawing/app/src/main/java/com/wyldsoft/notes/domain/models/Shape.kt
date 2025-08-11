package com.wyldsoft.notes.domain.models

import android.graphics.PointF
import java.util.UUID

/**
 * Represents a shape drawn on a note.
 * USED FOR INTERACTING WITH THE DATABASE I BELIEVE
 * @property id Unique identifier for the shape.
 * @property type Type of the shape (e.g., stroke, rectangle, circle).
 * @property points List of points defining the shape's geometry.
 * @property strokeWidth Width of the stroke used to draw the shape.
 * @property strokeColor Color of the stroke used to draw the shape.
 * @property pressure Pressure values for each point, if applicable.
 * @property timestamp Timestamp when the shape was created or modified.
 */
data class Shape(
    val id: String = UUID.randomUUID().toString(),
    val type: ShapeType = ShapeType.STROKE,
    val points: List<PointF>, // saved in NoteCoordinates
    val strokeWidth: Float,
    val strokeColor: Int,
    val pressure: List<Float> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class ShapeType {
    STROKE,
    RECTANGLE,
    CIRCLE,
    TRIANGLE,
    LINE
}