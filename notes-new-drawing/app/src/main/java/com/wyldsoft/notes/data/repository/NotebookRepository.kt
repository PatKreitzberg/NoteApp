package com.wyldsoft.notes.data.repository

import com.wyldsoft.notes.data.database.dao.NotebookDao
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.database.entities.NoteEntity
import com.wyldsoft.notes.data.database.entities.NoteNotebookCrossRef
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface NotebookRepository {
    suspend fun getNotebook(notebookId: String): NotebookEntity?
    fun getNotebooksInFolder(folderId: String): Flow<List<NotebookEntity>>
    suspend fun createNotebook(name: String, folderId: String): NotebookEntity
    suspend fun renameNotebook(notebookId: String, newName: String)
    suspend fun deleteNotebook(notebookId: String)
    fun getNotesInNotebook(notebookId: String): Flow<List<NoteEntity>>
    suspend fun getFirstNoteInNotebook(notebookId: String): NoteEntity?
    suspend fun getNotesInNotebookOnce(notebookId: String): List<NoteEntity>
    suspend fun createNoteInNotebook(notebookId: String): NoteEntity
}

class NotebookRepositoryImpl(
    private val notebookDao: NotebookDao,
    private val noteDao: NoteDao
) : NotebookRepository {
    
    override suspend fun getNotebook(notebookId: String): NotebookEntity? {
        return notebookDao.getNotebook(notebookId)
    }
    
    override fun getNotebooksInFolder(folderId: String): Flow<List<NotebookEntity>> {
        return notebookDao.getNotebooksByFolder(folderId)
    }
    
    override suspend fun createNotebook(name: String, folderId: String): NotebookEntity {
        val notebook = NotebookEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            folderId = folderId
        )
        notebookDao.insert(notebook)
        
        // Create first note automatically
        val firstNote = NoteEntity(
            id = UUID.randomUUID().toString(),
            title = "Page 1",
            parentNotebookId = notebook.id
        )
        noteDao.insert(firstNote)
        
        // Create cross-reference
        noteDao.insertNoteNotebookCrossRef(
            NoteNotebookCrossRef(
                noteId = firstNote.id,
                notebookId = notebook.id
            )
        )
        
        return notebook
    }
    
    override suspend fun renameNotebook(notebookId: String, newName: String) {
        val notebook = notebookDao.getNotebook(notebookId) ?: return
        notebookDao.update(
            notebook.copy(
                name = newName,
                modifiedAt = System.currentTimeMillis()
            )
        )
    }
    
    override suspend fun deleteNotebook(notebookId: String) {
        notebookDao.deleteById(notebookId)
    }
    
    override fun getNotesInNotebook(notebookId: String): Flow<List<NoteEntity>> {
        return notebookDao.getNotesInNotebook(notebookId)
    }
    
    override suspend fun getFirstNoteInNotebook(notebookId: String): NoteEntity? {
        return notebookDao.getFirstNoteInNotebook(notebookId)
    }

    override suspend fun getNotesInNotebookOnce(notebookId: String): List<NoteEntity> {
        return notebookDao.getNotesInNotebookOnce(notebookId)
    }

    override suspend fun createNoteInNotebook(notebookId: String): NoteEntity {
        val existingNotes = notebookDao.getNotesInNotebookOnce(notebookId)
        val pageNumber = existingNotes.size + 1

        val newNote = NoteEntity(
            id = UUID.randomUUID().toString(),
            title = "Page $pageNumber",
            parentNotebookId = notebookId
        )
        noteDao.insert(newNote)

        noteDao.insertNoteNotebookCrossRef(
            NoteNotebookCrossRef(
                noteId = newNote.id,
                notebookId = notebookId
            )
        )

        return newNote
    }
}