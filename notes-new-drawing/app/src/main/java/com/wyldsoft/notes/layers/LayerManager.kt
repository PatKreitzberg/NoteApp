package com.wyldsoft.notes.layers

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.data.database.dao.ShapeDao
import com.wyldsoft.notes.data.database.entities.LayerEntity
import com.wyldsoft.notes.data.database.repository.LayerRepository
import com.wyldsoft.notes.data.database.repository.ShapeRepository

private const val TAG = "LayerManager"

class LayerManager(
    private val layerRepository: LayerRepository,
    private val shapeRepository: ShapeRepository
) {

    /**
     * Loads layers for the note. If no layers exist, auto-creates Layer 1
     * for backward compatibility with notes created before layers were added.
     */
    suspend fun loadLayersForNote(noteId: String): List<LayerEntity> {
        Log.d(TAG, "loadLayersForNote noteId=$noteId")
        var layers = layerRepository.getLayersForNote(noteId)
        if (layers.isEmpty()) {
            Log.d(TAG, "loadLayersForNote: no layers found, creating default Layer 1")
            val defaultLayer = LayerEntity(
                id = NanoIdUtils.randomNanoId(),
                noteId = noteId,
                position = 1,
                name = "Layer 1",
                visible = true
            )
            layerRepository.insert(defaultLayer)
            layers = listOf(defaultLayer)
        }
        return layers
    }

    /**
     * Adds a new layer at the next available position for the note.
     * Returns the newly created layer.
     */
    suspend fun addLayer(noteId: String): LayerEntity {
        Log.d(TAG, "addLayer noteId=$noteId")
        val maxPosition = layerRepository.getMaxPositionForNote(noteId) ?: 0
        val newPosition = maxPosition + 1
        val newLayer = LayerEntity(
            id = NanoIdUtils.randomNanoId(),
            noteId = noteId,
            position = newPosition,
            name = "Layer $newPosition",
            visible = true
        )
        layerRepository.insert(newLayer)
        Log.d(TAG, "addLayer: created layer position=$newPosition id=${newLayer.id}")
        return newLayer
    }

    /**
     * Deletes a layer and all shapes belonging to that layer position in the note.
     */
    suspend fun deleteLayer(layer: LayerEntity, shapeDao: ShapeDao) {
        Log.d(TAG, "deleteLayer layerId=${layer.id} position=${layer.position}")
        shapeDao.deleteShapesForNoteAndLayer(layer.noteId, layer.position)
        layerRepository.delete(layer)
    }

    /**
     * Renames an existing layer.
     */
    suspend fun renameLayer(layer: LayerEntity, newName: String) {
        Log.d(TAG, "renameLayer layerId=${layer.id} newName=$newName")
        val updated = layer.copy(name = newName)
        layerRepository.update(updated)
    }

    /**
     * Toggles the visibility of an existing layer.
     */
    suspend fun toggleVisibility(layer: LayerEntity) {
        Log.d(TAG, "toggleVisibility layerId=${layer.id} currentVisible=${layer.visible}")
        val updated = layer.copy(visible = !layer.visible)
        layerRepository.update(updated)
    }
}
