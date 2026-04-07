package com.wyldsoft.notes.data.database.repository

import android.util.Log
import com.wyldsoft.notes.data.database.dao.UndoHistoryDao
import com.wyldsoft.notes.data.database.entities.UndoHistoryEntity

class UndoHistoryRepository(private val dao: UndoHistoryDao) {
    private val TAG = "UndoHistoryRepository"

    suspend fun saveAction(entity: UndoHistoryEntity) {
        Log.d(TAG, "saveAction id=${entity.id} type=${entity.actionType} isUndo=${entity.isUndoStack}")
        dao.insert(entity)
    }

    suspend fun getActionsForNote(noteId: String): List<UndoHistoryEntity> {
        Log.d(TAG, "getActionsForNote noteId=$noteId")
        return dao.getForNote(noteId)
    }

    suspend fun updateStackType(id: String, isUndoStack: Boolean) {
        Log.d(TAG, "updateStackType id=$id isUndoStack=$isUndoStack")
        dao.updateStackType(id, isUndoStack)
    }

    suspend fun clearRedoStack(noteId: String) {
        Log.d(TAG, "clearRedoStack noteId=$noteId")
        dao.clearRedoStack(noteId)
    }

    suspend fun clearAll(noteId: String) {
        Log.d(TAG, "clearAll noteId=$noteId")
        dao.clearAll(noteId)
    }
}
