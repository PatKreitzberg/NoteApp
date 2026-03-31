package com.wyldsoft.notes.data.database.repository

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.entities.NoteEntity

class NoteRepository(private val noteDao: NoteDao) {
    companion object {
        private const val TAG = "NoteRepository"
    }

    suspend fun createNote(
        title: String = "Untitled",
        notebookId: String? = null,
        folderId: String? = null
    ): NoteEntity {
        Log.d(TAG, "createNote title=$title notebookId=$notebookId folderId=$folderId")
        val now = System.currentTimeMillis()
        val note = NoteEntity(
            id = NanoIdUtils.randomNanoId(),
            title = title,
            parentNotebookId = notebookId,
            folderId = folderId,
            createdAt = now,
            modifiedAt = now
        )
        noteDao.insert(note)
        return note
    }

    suspend fun getById(id: String): NoteEntity? {
        Log.d(TAG, "getById id=$id")
        return noteDao.getById(id)
    }

    suspend fun getByNotebook(notebookId: String): List<NoteEntity> {
        Log.d(TAG, "getByNotebook notebookId=$notebookId")
        return noteDao.getByNotebook(notebookId)
    }

    suspend fun updateViewport(noteId: String, scale: Float, scrollX: Float, scrollY: Float) {
        Log.d(TAG, "updateViewport noteId=$noteId scale=$scale scrollX=$scrollX scrollY=$scrollY")
        noteDao.updateViewport(noteId, scale, scrollX, scrollY)
    }

    suspend fun updatePagination(noteId: String, enabled: Boolean) {
        Log.d(TAG, "updatePagination noteId=$noteId enabled=$enabled")
        noteDao.updatePagination(noteId, enabled)
    }

    suspend fun update(note: NoteEntity) {
        Log.d(TAG, "update noteId=${note.id}")
        noteDao.update(note)
    }
}
