package com.wyldsoft.notes.geometry

import com.wyldsoft.notes.domain.models.ShapeType

enum class GeometricShapeType {
    LINE,
    TRIANGLE,
    SQUARE,
    CIRCLE;

    fun toDomainShapeType(): ShapeType = when (this) {
        LINE -> ShapeType.LINE
        TRIANGLE -> ShapeType.TRIANGLE
        SQUARE -> ShapeType.RECTANGLE
        CIRCLE -> ShapeType.CIRCLE
    }

    fun displayName(): String = when (this) {
        LINE -> "Line"
        TRIANGLE -> "Triangle"
        SQUARE -> "Square"
        CIRCLE -> "Circle"
    }
}
