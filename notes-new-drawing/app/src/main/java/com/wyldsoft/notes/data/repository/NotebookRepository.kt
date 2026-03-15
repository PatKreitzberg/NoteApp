package com.wyldsoft.notes.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.wyldsoft.notes.data.database.dao.DeletedItemDao
import com.wyldsoft.notes.data.database.dao.NotebookDao
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.dao.ShapeDao
import com.wyldsoft.notes.data.database.entities.DeletedItemEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.database.entities.NoteEntity
import com.wyldsoft.notes.data.database.entities.NoteNotebookCrossRef
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface NotebookRepository {
    suspend fun getNotebook(notebookId: String): NotebookEntity?
    fun getNotebooksInFolder(folderId: String): Flow<List<NotebookEntity>>
    suspend fun createNotebook(name: String, folderId: String, isPaginationEnabled: Boolean = true, paperSize: String = "LETTER", paperTemplate: String = "BLANK"): NotebookEntity
    suspend fun renameNotebook(notebookId: String, newName: String)
    suspend fun deleteNotebook(notebookId: String)
    fun getNotesInNotebook(notebookId: String): Flow<List<NoteEntity>>
    suspend fun getFirstNoteInNotebook(notebookId: String): NoteEntity?
    suspend fun getNotesInNotebookOnce(notebookId: String): List<NoteEntity>
    suspend fun createNoteInNotebook(notebookId: String, isPaginationEnabled: Boolean = true, paperSize: String = "LETTER", paperTemplate: String = "BLANK"): NoteEntity
    suspend fun moveNotebook(notebookId: String, targetFolderId: String)
    suspend fun getAllNotebooks(): List<NotebookEntity>
    suspend fun moveNotebookToTrash(notebookId: String)
    suspend fun restoreNotebook(notebookId: String)
    suspend fun permanentlyDeleteNotebook(notebookId: String)
}

class NotebookRepositoryImpl(
    private val db: RoomDatabase,
    private val notebookDao: NotebookDao,
    private val noteDao: NoteDao,
    private val shapeDao: ShapeDao,
    private val deletedItemDao: DeletedItemDao,
    private val folderRepository: FolderRepository
) : NotebookRepository {
    
    override suspend fun getNotebook(notebookId: String): NotebookEntity? {
        return notebookDao.getNotebook(notebookId)
    }
    
    override fun getNotebooksInFolder(folderId: String): Flow<List<NotebookEntity>> {
        return notebookDao.getNotebooksByFolder(folderId)
    }
    
    override suspend fun createNotebook(name: String, folderId: String, isPaginationEnabled: Boolean, paperSize: String, paperTemplate: String): NotebookEntity {
        val notebook = NotebookEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            folderId = folderId
        )
        notebookDao.insert(notebook)

        val firstNote = NoteEntity(
            id = UUID.randomUUID().toString(),
            title = "$name-Page 1",
            parentNotebookId = notebook.id,
            isPaginationEnabled = isPaginationEnabled,
            paperSize = paperSize,
            paperTemplate = paperTemplate
        )
        noteDao.insert(firstNote)
        noteDao.insertNoteNotebookCrossRef(
            NoteNotebookCrossRef(noteId = firstNote.id, notebookId = notebook.id)
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
        val notesInNotebook = notebookDao.getNotesInNotebookOnce(notebookId)
        for (note in notesInNotebook) {
            val otherNotebooks = noteDao.getNotebooksForNote(note.id).filter { it != notebookId }
            if (otherNotebooks.isEmpty() && note.folderId == null) {
                shapeDao.deleteAllForNote(note.id)
                deletedItemDao.insert(DeletedItemEntity(entityId = note.id, entityType = "note"))
                noteDao.deleteById(note.id)
            } else if (note.parentNotebookId == notebookId && otherNotebooks.isNotEmpty()) {
                noteDao.update(note.copy(
                    parentNotebookId = otherNotebooks.first(),
                    modifiedAt = System.currentTimeMillis()
                ))
            }
        }
        deletedItemDao.insert(DeletedItemEntity(entityId = notebookId, entityType = "notebook"))
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

    override suspend fun createNoteInNotebook(notebookId: String, isPaginationEnabled: Boolean, paperSize: String, paperTemplate: String): NoteEntity {
        val notebook = notebookDao.getNotebook(notebookId)
        val existingNotes = notebookDao.getNotesInNotebookOnce(notebookId)
        val pageNumber = existingNotes.size + 1
        val notebookName = notebook?.name ?: "Notebook"

        val newNote = NoteEntity(
            id = UUID.randomUUID().toString(),
            title = "$notebookName-Page $pageNumber",
            parentNotebookId = notebookId,
            isPaginationEnabled = isPaginationEnabled,
            paperSize = paperSize,
            paperTemplate = paperTemplate
        )
        noteDao.insert(newNote)
        noteDao.insertNoteNotebookCrossRef(
            NoteNotebookCrossRef(noteId = newNote.id, notebookId = notebookId)
        )
        return newNote
    }

    override suspend fun moveNotebook(notebookId: String, targetFolderId: String) {
        val notebook = notebookDao.getNotebook(notebookId) ?: return
        notebookDao.update(notebook.copy(
            folderId = targetFolderId,
            modifiedAt = System.currentTimeMillis()
        ))
    }

    override suspend fun getAllNotebooks(): List<NotebookEntity> {
        return notebookDao.getAllNotebookEntities()
    }

    override suspend fun moveNotebookToTrash(notebookId: String) {
        val notebook = notebookDao.getNotebook(notebookId) ?: return
        folderRepository.ensureTrashFolderExists()
        db.withTransaction {
            deletedItemDao.insert(DeletedItemEntity(
                entityId = notebookId,
                entityType = "notebook",
                originalParentId = notebook.folderId
            ))
            notebookDao.update(notebook.copy(
                folderId = FolderRepository.TRASH_FOLDER_ID,
                modifiedAt = System.currentTimeMillis()
            ))
        }
    }

    override suspend fun restoreNotebook(notebookId: String) {
        val notebook = notebookDao.getNotebook(notebookId) ?: return
        val deletedItem = deletedItemDao.getByEntityId(notebookId)
        val originalParentId = deletedItem?.originalParentId
        val targetFolderId = if (originalParentId != null) {
            val folder = folderRepository.getFolder(originalParentId)
            if (folder != null && folder.parentFolderId != FolderRepository.TRASH_FOLDER_ID) {
                originalParentId
            } else {
                FolderRepository.ROOT_FOLDER_ID
            }
        } else {
            FolderRepository.ROOT_FOLDER_ID
        }
        notebookDao.update(notebook.copy(
            folderId = targetFolderId,
            modifiedAt = System.currentTimeMillis()
        ))
    }

    override suspend fun permanentlyDeleteNotebook(notebookId: String) {
        deleteNotebook(notebookId)
    }
}