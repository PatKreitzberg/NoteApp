package com.wyldsoft.notes.undoredo

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.rendering.DrawingPipeline
import com.wyldsoft.notes.rendering.PaginationManager
import com.wyldsoft.notes.selection.SelectionManager

/**
 * Records a completed separation operation.
 * Stores shape IDs and the uniform Y delta applied to all shapes below the split line.
 * Also tracks how many pages were auto-created so they can be removed on undo.
 * Undo translates shapes back by -deltaY and removes added pages.
 * Redo re-applies the translation and re-adds pages.
 */
class SeparationAction(
    val shapeIds: List<String>,
    val deltaY: Float,
    val pagesAdded: Int,
    private val pipeline: DrawingPipeline,
    private val paginationManager: PaginationManager,
    private val selectionManager: SelectionManager,
    override val id: String = NanoIdUtils.randomNanoId()
) : ActionInterface {
    private val TAG = "SeparationAction"

    override suspend fun undo() {
        Log.d(TAG, "undo ${shapeIds.size} shapes by -$deltaY, removing $pagesAdded pages")
        for (shapeId in shapeIds) {
            val shape = pipeline.getShapeById(shapeId) ?: continue
            selectionManager.translateShape(shape, 0f, -deltaY)
            pipeline.updateShape(shape)
        }
        paginationManager.removePages(pagesAdded)
    }

    override suspend fun redo() {
        Log.d(TAG, "redo ${shapeIds.size} shapes by $deltaY, adding $pagesAdded pages")
        for (shapeId in shapeIds) {
            val shape = pipeline.getShapeById(shapeId) ?: continue
            selectionManager.translateShape(shape, 0f, deltaY)
            pipeline.updateShape(shape)
        }
        paginationManager.addPages(pagesAdded)
    }
}
