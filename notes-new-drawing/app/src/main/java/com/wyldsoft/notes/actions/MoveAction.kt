package com.wyldsoft.notes.actions

import android.graphics.PointF
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager

/**
 * Records a move operation so it can be undone/redone.
 * Stores the original and moved domain shapes for each affected shape.
 */
class MoveAction(
    internal val noteId: String,
    internal val originalShapes: List<Shape>,
    internal val dx: Float,
    internal val dy: Float,
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
            val movedPoints = original.points.map { PointF(it.x + dx, it.y + dy) }
            val movedShape = original.copy(points = movedPoints)
            noteRepository.updateShape(noteId, movedShape)
            ActionUtils.updateSdkShapePoints(original, movedPoints, shapesManager)
        }
        ActionUtils.refreshBitmap(shapesManager, bitmapManager)
    }
}
