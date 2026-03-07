package com.wyldsoft.notes.geometry

import android.graphics.PointF
import kotlin.math.*

/**
 * Computes the outline points for each geometric shape type.
 *
 * All shapes are centered at (startX, startY).
 * The distance from start to end determines size (radius).
 * The angle from start to end determines rotation.
 *
 * LINE: start point to end point.
 * CIRCLE: 36-point polygon approximation, radius = distance.
 * SQUARE: 4 corners, one corner pointing toward drag direction.
 * TRIANGLE: equilateral, one vertex pointing toward drag direction.
 */
object GeometryShapeCalculator {

    fun calculate(
        shapeType: GeometricShapeType,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): List<PointF> = when (shapeType) {
        GeometricShapeType.LINE -> calculateLine(startX, startY, endX, endY)
        GeometricShapeType.CIRCLE -> calculateCircle(startX, startY, endX, endY)
        GeometricShapeType.SQUARE -> calculateSquare(startX, startY, endX, endY)
        GeometricShapeType.TRIANGLE -> calculateTriangle(startX, startY, endX, endY)
    }

    private fun calculateLine(startX: Float, startY: Float, endX: Float, endY: Float): List<PointF> =
        listOf(PointF(startX, startY), PointF(endX, endY))

    private fun calculateCircle(
        centerX: Float, centerY: Float,
        endX: Float, endY: Float
    ): List<PointF> {
        val radius = sqrt((endX - centerX).pow(2) + (endY - centerY).pow(2))
        if (radius < 1f) return listOf(PointF(centerX, centerY))
        val steps = 36
        return (0..steps).map { i ->
            val angle = 2.0 * PI * i / steps
            PointF(
                centerX + radius * cos(angle).toFloat(),
                centerY + radius * sin(angle).toFloat()
            )
        }
    }

    // Square: center at start, one corner pointing toward drag direction (half-diagonal = radius)
    private fun calculateSquare(
        centerX: Float, centerY: Float,
        endX: Float, endY: Float
    ): List<PointF> {
        val radius = sqrt((endX - centerX).pow(2) + (endY - centerY).pow(2))
        if (radius < 1f) return listOf(PointF(centerX, centerY))
        val theta = atan2(endY - centerY, endX - centerX).toDouble()
        return (0..4).map { k ->
            val angle = theta + k * (PI / 2.0)
            PointF(
                centerX + radius * cos(angle).toFloat(),
                centerY + radius * sin(angle).toFloat()
            )
        }
    }

    // Equilateral triangle: center at start, one vertex pointing toward drag, circumradius = distance
    private fun calculateTriangle(
        centerX: Float, centerY: Float,
        endX: Float, endY: Float
    ): List<PointF> {
        val radius = sqrt((endX - centerX).pow(2) + (endY - centerY).pow(2))
        if (radius < 1f) return listOf(PointF(centerX, centerY))
        val theta = atan2(endY - centerY, endX - centerX).toDouble()
        val points = (0..2).map { k ->
            val angle = theta + k * (2.0 * PI / 3.0)
            PointF(
                centerX + radius * cos(angle).toFloat(),
                centerY + radius * sin(angle).toFloat()
            )
        }
        // Close the triangle
        return points + listOf(points[0])
    }
}
