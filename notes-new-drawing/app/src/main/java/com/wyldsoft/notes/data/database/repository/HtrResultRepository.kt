package com.wyldsoft.notes.data.database.repository

import android.util.Log
import com.wyldsoft.notes.data.database.dao.HtrResultDao
import com.wyldsoft.notes.data.database.dao.HtrSearchRow
import com.wyldsoft.notes.data.database.entities.HtrResultEntity

class HtrResultRepository(private val htrResultDao: HtrResultDao) {
    companion object {
        private const val TAG = "HtrResultRepository"
    }

    suspend fun upsert(entity: HtrResultEntity) {
        Log.d(TAG, "upsert id=${entity.id} noteId=${entity.noteId} text='${entity.text}'")
        htrResultDao.insert(entity)
    }

    suspend fun getByNoteId(noteId: String): List<HtrResultEntity> {
        Log.d(TAG, "getByNoteId noteId=$noteId")
        return htrResultDao.getByNoteId(noteId)
    }

    suspend fun deleteByNoteId(noteId: String) {
        Log.d(TAG, "deleteByNoteId noteId=$noteId")
        htrResultDao.deleteByNoteId(noteId)
    }

    suspend fun searchAcrossNotes(query: String): List<HtrSearchRow> {
        Log.d(TAG, "searchAcrossNotes query='$query'")
        return htrResultDao.searchAcrossNotes(query)
    }
}
