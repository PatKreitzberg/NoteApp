package com.wyldsoft.notes.actions

import android.graphics.PointF
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import kotlin.math.cos
import kotlin.math.sin

enum class TransformType { SCALE, ROTATE }

/**
 * Records a scale or rotate operation so it can be undone/redone.
 * Stores the original domain shapes before the transform.
 */
class TransformAction(
    private val noteId: String,
    private val originalShapes: List<Shape>,
    private val transformType: TransformType,
    private val param: Float, // scaleFactor for SCALE, angleRad for ROTATE
    private val centerX: Float,
    private val centerY: Float,
    private val noteRepository: NoteRepository,
    private val shapesManager: ShapesManager,
    private val bitmapManager: BitmapManager
) : ActionInterface {

    override suspend fun undo() {
        for (original in originalShapes) {
            noteRepository.updateShape(noteId, original)
            val sdkShape = shapesManager.shapes().find { it.id == original.id }
            if (sdkShape != null) {
                val newList = TouchPointList()
                for ((idx, pt) in original.points.withIndex()) {
                    val pressure = original.pressure.getOrElse(idx) { 0.5f }
                    newList.add(TouchPoint(pt.x, pt.y, pressure, 1f, System.currentTimeMillis()))
                }
                sdkShape.touchPointList = newList
                sdkShape.updateShapeRect()
            }
        }
        bitmapManager.recreateBitmapFromShapes(shapesManager.shapes())
    }

    override suspend fun redo() {
        for (original in originalShapes) {
            val transformedPoints = when (transformType) {
                TransformType.SCALE -> original.points.map { pt ->
                    PointF(
                        centerX + (pt.x - centerX) * param,
                        centerY + (pt.y - centerY) * param
                    )
                }
                TransformType.ROTATE -> {
                    val cosA = cos(param)
                    val sinA = sin(param)
                    original.points.map { pt ->
                        val dx = pt.x - centerX
                        val dy = pt.y - centerY
                        PointF(
                            centerX + dx * cosA - dy * sinA,
                            centerY + dx * sinA + dy * cosA
                        )
                    }
                }
            }
            val transformedShape = original.copy(points = transformedPoints)
            noteRepository.updateShape(noteId, transformedShape)

            val sdkShape = shapesManager.shapes().find { it.id == original.id }
            if (sdkShape != null) {
                val newList = TouchPointList()
                for ((idx, pt) in transformedPoints.withIndex()) {
                    val pressure = original.pressure.getOrElse(idx) { 0.5f }
                    newList.add(TouchPoint(pt.x, pt.y, pressure, 1f, System.currentTimeMillis()))
                }
                sdkShape.touchPointList = newList
                sdkShape.updateShapeRect()
            }
        }
        bitmapManager.recreateBitmapFromShapes(shapesManager.shapes())
    }
}
