package com.wyldsoft.notes.actions

import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager

class MoveLayerAction(
    private val shapeIds: List<String>,
    private val fromLayer: Int,
    private val toLayer: Int,
    private val noteRepository: NoteRepository,
    private val shapesManager: ShapesManager,
    private val bitmapManager: BitmapManager
) : ActionInterface {

    override suspend fun undo() {
        noteRepository.updateShapesLayer(shapeIds, fromLayer)
        updateInMemoryLayers(fromLayer)
        ActionUtils.refreshBitmap(shapesManager, bitmapManager)
    }

    override suspend fun redo() {
        noteRepository.updateShapesLayer(shapeIds, toLayer)
        updateInMemoryLayers(toLayer)
        ActionUtils.refreshBitmap(shapesManager, bitmapManager)
    }

    private fun updateInMemoryLayers(layer: Int) {
        val idSet = shapeIds.toSet()
        for (shape in shapesManager.shapes()) {
            if (shape.id in idSet) {
                shape.layer = layer
            }
        }
    }
}
