package com.wyldsoft.notes.data.repository

import com.wyldsoft.notes.data.database.dao.DeletedItemDao
import com.wyldsoft.notes.data.database.dao.FolderDao
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.dao.NotebookDao
import com.wyldsoft.notes.data.database.dao.ShapeDao
import com.wyldsoft.notes.data.database.entities.DeletedItemEntity
import com.wyldsoft.notes.data.database.entities.FolderEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface FolderRepository {
    suspend fun getFolder(folderId: String): FolderEntity?
    fun getSubfolders(parentId: String?): Flow<List<FolderEntity>>
    suspend fun getRootFolder(): FolderEntity
    suspend fun createFolder(name: String, parentId: String): FolderEntity
    suspend fun renameFolder(folderId: String, newName: String)
    suspend fun deleteFolder(folderId: String)
    suspend fun getFolderPath(folderId: String): List<FolderEntity>
    suspend fun moveFolder(folderId: String, targetParentFolderId: String)
    suspend fun getAllFolders(): List<FolderEntity>
    suspend fun emptyTrash()
    suspend fun ensureTrashFolderExists()

    companion object {
        const val TRASH_FOLDER_ID = "trash"
        const val ROOT_FOLDER_ID = "root"
    }
}

class FolderRepositoryImpl(
    private val folderDao: FolderDao,
    private val notebookDao: NotebookDao,
    private val noteDao: NoteDao,
    private val shapeDao: ShapeDao,
    private val deletedItemDao: DeletedItemDao? = null
) : FolderRepository {

    override suspend fun getFolder(folderId: String): FolderEntity? {
        return folderDao.getFolder(folderId)
    }

    override fun getSubfolders(parentId: String?): Flow<List<FolderEntity>> {
        return folderDao.getSubfolders(parentId)
    }

    override suspend fun getRootFolder(): FolderEntity {
        return folderDao.getRootFolder() ?: run {
            val root = FolderEntity(id = "root", name = "Root", parentFolderId = null)
            folderDao.insert(root)
            root
        }
    }

    override suspend fun createFolder(name: String, parentId: String): FolderEntity {
        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            parentFolderId = parentId
        )
        folderDao.insert(folder)
        return folder
    }

    override suspend fun renameFolder(folderId: String, newName: String) {
        if (folderId == FolderRepository.TRASH_FOLDER_ID || folderId == FolderRepository.ROOT_FOLDER_ID) return
        val folder = folderDao.getFolder(folderId) ?: return
        folderDao.update(folder.copy(name = newName, modifiedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteFolder(folderId: String) {
        if (folderId == FolderRepository.TRASH_FOLDER_ID || folderId == FolderRepository.ROOT_FOLDER_ID) return
        val folder = folderDao.getFolder(folderId) ?: return
        ensureTrashFolder()
        folderDao.update(folder.copy(
            parentFolderId = FolderRepository.TRASH_FOLDER_ID,
            modifiedAt = System.currentTimeMillis()
        ))
    }

    override suspend fun ensureTrashFolderExists() = ensureTrashFolder()

    private suspend fun ensureTrashFolder() {
        val existing = folderDao.getFolder(FolderRepository.TRASH_FOLDER_ID)
        getRootFolder() // ensure root exists first
        if (existing == null) {
            folderDao.insert(FolderEntity(id = FolderRepository.TRASH_FOLDER_ID, name = "Trash", parentFolderId = FolderRepository.ROOT_FOLDER_ID))
        } else if (existing.parentFolderId != FolderRepository.ROOT_FOLDER_ID) {
            folderDao.update(existing.copy(parentFolderId = FolderRepository.ROOT_FOLDER_ID))
        }
    }

    override suspend fun emptyTrash() {
        val subfolders = folderDao.getSubfoldersOnce(FolderRepository.TRASH_FOLDER_ID)
        for (subfolder in subfolders) {
            deleteFolderRecursive(subfolder.id)
        }
        val notebooks = notebookDao.getNotebooksInFolderOnce(FolderRepository.TRASH_FOLDER_ID)
        for (notebook in notebooks) {
            val notes = notebookDao.getNotesInNotebookOnce(notebook.id)
            for (note in notes) {
                shapeDao.deleteAllForNote(note.id)
                deletedItemDao?.insert(DeletedItemEntity(entityId = note.id, entityType = "note"))
                noteDao.deleteById(note.id)
            }
            deletedItemDao?.insert(DeletedItemEntity(entityId = notebook.id, entityType = "notebook"))
            notebookDao.deleteById(notebook.id)
        }
        val looseNotes = noteDao.getLooseNotesInFolderOnce(FolderRepository.TRASH_FOLDER_ID)
        for (note in looseNotes) {
            shapeDao.deleteAllForNote(note.id)
            deletedItemDao?.insert(DeletedItemEntity(entityId = note.id, entityType = "note"))
            noteDao.deleteById(note.id)
        }
    }

    private suspend fun deleteFolderRecursive(folderId: String) {
        val subfolders = folderDao.getSubfoldersOnce(folderId)
        for (subfolder in subfolders) {
            deleteFolderRecursive(subfolder.id)
        }

        val notebooks = notebookDao.getNotebooksInFolderOnce(folderId)
        for (notebook in notebooks) {
            val notes = notebookDao.getNotesInNotebookOnce(notebook.id)
            for (note in notes) {
                val otherNotebooks = noteDao.getNotebooksForNote(note.id).filter { it != notebook.id }
                val hasOtherFolder = note.folderId != null
                if (otherNotebooks.isEmpty() && !hasOtherFolder) {
                    shapeDao.deleteAllForNote(note.id)
                    deletedItemDao?.insert(DeletedItemEntity(entityId = note.id, entityType = "note"))
                    noteDao.deleteById(note.id)
                } else if (note.parentNotebookId == notebook.id && otherNotebooks.isNotEmpty()) {
                    noteDao.update(note.copy(
                        parentNotebookId = otherNotebooks.first(),
                        modifiedAt = System.currentTimeMillis()
                    ))
                }
            }
            deletedItemDao?.insert(DeletedItemEntity(entityId = notebook.id, entityType = "notebook"))
        }

        val looseNotes = noteDao.getLooseNotesInFolderOnce(folderId)
        for (note in looseNotes) {
            shapeDao.deleteAllForNote(note.id)
            deletedItemDao?.insert(DeletedItemEntity(entityId = note.id, entityType = "note"))
            noteDao.deleteById(note.id)
        }

        deletedItemDao?.insert(DeletedItemEntity(entityId = folderId, entityType = "folder"))
        folderDao.deleteById(folderId)
    }

    override suspend fun getFolderPath(folderId: String): List<FolderEntity> {
        return folderDao.getFolderPath(folderId)
    }

    override suspend fun moveFolder(folderId: String, targetParentFolderId: String) {
        if (folderId == FolderRepository.TRASH_FOLDER_ID || folderId == FolderRepository.ROOT_FOLDER_ID) return
        if (folderId == targetParentFolderId) return
        val targetPath = folderDao.getFolderPath(targetParentFolderId)
        if (targetPath.any { it.id == folderId }) return
        val folder = folderDao.getFolder(folderId) ?: return
        folderDao.update(folder.copy(
            parentFolderId = targetParentFolderId,
            modifiedAt = System.currentTimeMillis()
        ))
    }

    override suspend fun getAllFolders(): List<FolderEntity> {
        return folderDao.getAllFoldersOnce()
    }
}
