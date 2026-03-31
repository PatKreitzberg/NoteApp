package com.wyldsoft.notes.data.database.repository

import android.util.Log
import com.wyldsoft.notes.data.database.dao.ShapeDao
import com.wyldsoft.notes.data.database.entities.ShapeEntity

class ShapeRepository(private val shapeDao: ShapeDao) {
    companion object {
        private const val TAG = "ShapeRepository"
    }

    suspend fun saveShape(shapeEntity: ShapeEntity) {
        Log.d(TAG, "saveShape id=${shapeEntity.id}")
        shapeDao.insert(shapeEntity)
    }

    suspend fun getShapesForNote(noteId: String): List<ShapeEntity> {
        Log.d(TAG, "getShapesForNote noteId=$noteId")
        return shapeDao.getByNoteId(noteId)
    }

    suspend fun deleteShape(shapeId: String) {
        Log.d(TAG, "deleteShape shapeId=$shapeId")
        shapeDao.deleteById(shapeId)
    }

    suspend fun deleteAllShapesForNote(noteId: String) {
        Log.d(TAG, "deleteAllShapesForNote noteId=$noteId")
        shapeDao.deleteByNoteId(noteId)
    }
}
