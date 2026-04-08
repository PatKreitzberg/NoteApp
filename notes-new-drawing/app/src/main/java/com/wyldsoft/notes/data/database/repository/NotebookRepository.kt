package com.wyldsoft.notes.data.database.repository

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.data.database.dao.DeletedItemDao
import com.wyldsoft.notes.data.database.dao.NotebookDao
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.entities.DeletedItemEntity
import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.database.entities.NoteEntity

class NotebookRepository(
    private val notebookDao: NotebookDao,
    private val noteDao: NoteDao,
    private val deletedItemDao: DeletedItemDao? = null
) {
    companion object {
        private const val TAG = "NotebookRepository"
    }

    suspend fun createNotebookWithFirstNote(
        name: String,
        folderId: String,
        paginationEnabled: Boolean = true
    ): Pair<NotebookEntity, NoteEntity> {
        Log.d(TAG, "createNotebookWithFirstNote name=$name folderId=$folderId paginationEnabled=$paginationEnabled")
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
            modifiedAt = now,
            isPaginationEnabled = paginationEnabled
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

    suspend fun renameNotebook(id: String, newName: String) {
        Log.d(TAG, "renameNotebook id=$id newName=$newName")
        notebookDao.renameNotebook(id, newName, System.currentTimeMillis())
    }

    suspend fun updateTemplate(id: String, template: String) {
        Log.d(TAG, "updateTemplate id=$id template=$template")
        notebookDao.updateTemplate(id, template)
    }

    suspend fun updatePagination(id: String, enabled: Boolean) {
        Log.d(TAG, "updatePagination id=$id enabled=$enabled")
        notebookDao.updatePagination(id, enabled)
    }

    suspend fun moveNotebook(id: String, newFolderId: String) {
        Log.d(TAG, "moveNotebook id=$id newFolderId=$newFolderId")
        notebookDao.moveNotebook(id, newFolderId, null, System.currentTimeMillis())
    }

    suspend fun moveToTrash(id: String) {
        Log.d(TAG, "moveToTrash id=$id")
        val notebook = notebookDao.getById(id) ?: return
        val originalFolder = notebook.folderId
        notebookDao.moveNotebook(id, FolderEntity.TRASH_ID, originalFolder, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(id: String) {
        Log.d(TAG, "restoreFromTrash id=$id")
        val notebook = notebookDao.getById(id) ?: return
        val destination = notebook.trashedFromId ?: FolderEntity.ROOT_ID
        notebookDao.moveNotebook(id, destination, null, System.currentTimeMillis())
    }

    suspend fun getNotebooksInTrash(): List<NotebookEntity> {
        Log.d(TAG, "getNotebooksInTrash")
        return notebookDao.getNotebooksInTrash()
    }

    suspend fun permanentlyDelete(notebook: NotebookEntity) {
        Log.d(TAG, "permanentlyDelete id=${notebook.id}")
        notebookDao.deleteById(notebook.id)
        deletedItemDao?.insert(
            DeletedItemEntity(
                entityId = notebook.id,
                entityType = "notebook",
                deletedAt = System.currentTimeMillis(),
                originalParentId = notebook.trashedFromId
            )
        )
    }
}
