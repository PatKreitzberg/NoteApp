package com.wyldsoft.notes.data.database.repository

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.data.database.dao.NotebookDao
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.database.entities.NoteEntity

class NotebookRepository(
    private val notebookDao: NotebookDao,
    private val noteDao: NoteDao
) {
    companion object {
        private const val TAG = "NotebookRepository"
    }

    suspend fun createNotebookWithFirstNote(
        name: String,
        folderId: String
    ): Pair<NotebookEntity, NoteEntity> {
        Log.d(TAG, "createNotebookWithFirstNote name=$name folderId=$folderId")
        val now = System.currentTimeMillis()
        val notebook = NotebookEntity(
            id = NanoIdUtils.randomNanoId(),
            name = name,
            folderId = folderId,
            createdAt = now,
            modifiedAt = now
        )
        notebookDao.insert(notebook)

        val note = NoteEntity(
            id = NanoIdUtils.randomNanoId(),
            title = "Page 1",
            parentNotebookId = notebook.id,
            createdAt = now,
            modifiedAt = now
        )
        noteDao.insert(note)

        return Pair(notebook, note)
    }

    suspend fun getByFolder(folderId: String): List<NotebookEntity> {
        Log.d(TAG, "getByFolder folderId=$folderId")
        return notebookDao.getByFolder(folderId)
    }

    suspend fun getById(id: String): NotebookEntity? {
        Log.d(TAG, "getById id=$id")
        return notebookDao.getById(id)
    }
}
