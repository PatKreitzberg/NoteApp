package com.wyldsoft.notes.actions

import android.graphics.PointF
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
    internal val noteId: String,
    internal val originalShapes: List<Shape>,
    internal val transformType: TransformType,
    internal val param: Float, // scaleFactor for SCALE, angleRad for ROTATE
    internal val centerX: Float,
    internal val centerY: Float,
    private val noteRepository: NoteRepository,
    private val shapesManager: ShapesManager,
    private val bitmapManager: BitmapManager
) : ActionInterface {

    override suspend fun undo() {
        for (original in originalShapes) {
            noteRepository.updateShape(noteId, original)
            ActionUtils.updateSdkShapePoints(original, original.points, shapesManager)
        }
        ActionUtils.refreshBitmap(shapesManager, bitmapManager)
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
            ActionUtils.updateSdkShapePoints(original, transformedPoints, shapesManager)
        }
        ActionUtils.refreshBitmap(shapesManager, bitmapManager)
    }
}
