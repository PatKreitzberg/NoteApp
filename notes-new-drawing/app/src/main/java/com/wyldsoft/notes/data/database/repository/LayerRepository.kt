package com.wyldsoft.notes.data.database.repository

import android.util.Log
import com.wyldsoft.notes.data.database.dao.LayerDao
import com.wyldsoft.notes.data.database.entities.LayerEntity

class LayerRepository(private val layerDao: LayerDao) {
    companion object {
        private const val TAG = "LayerRepository"
    }

    suspend fun getLayersForNote(noteId: String): List<LayerEntity> {
        Log.d(TAG, "getLayersForNote noteId=$noteId")
        return layerDao.getLayersForNote(noteId)
    }

    suspend fun insert(layer: LayerEntity) {
        Log.d(TAG, "insert layerId=${layer.id} noteId=${layer.noteId} position=${layer.position}")
        layerDao.insert(layer)
    }

    suspend fun update(layer: LayerEntity) {
        Log.d(TAG, "update layerId=${layer.id} name=${layer.name} visible=${layer.visible}")
        layerDao.update(layer)
    }

    suspend fun delete(layer: LayerEntity) {
        Log.d(TAG, "delete layerId=${layer.id}")
        layerDao.delete(layer)
    }

    suspend fun deleteByNoteId(noteId: String) {
        Log.d(TAG, "deleteByNoteId noteId=$noteId")
        layerDao.deleteByNoteId(noteId)
    }

    suspend fun getMaxPositionForNote(noteId: String): Int? {
        Log.d(TAG, "getMaxPositionForNote noteId=$noteId")
        return layerDao.getMaxPositionForNote(noteId)
    }
}
