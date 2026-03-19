package com.wyldsoft.notes.actions

import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.shapemanagement.ShapesManager

class MoveLayerAction(
    internal val shapeIds: List<String>,
    internal val fromLayer: Int,
    internal val toLayer: Int,
    private val noteRepository: NoteRepository,
    private val shapesManager: ShapesManager
) : ActionInterface {

    override suspend fun undo() {
        noteRepository.updateShapesLayer(shapeIds, fromLayer)
        updateInMemoryLayers(fromLayer)
    }

    override suspend fun redo() {
        noteRepository.updateShapesLayer(shapeIds, toLayer)
        updateInMemoryLayers(toLayer)
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
