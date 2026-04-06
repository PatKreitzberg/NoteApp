package com.wyldsoft.notes.data.database.repository

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.data.database.dao.DeletedItemDao
import com.wyldsoft.notes.data.database.dao.FolderDao
import com.wyldsoft.notes.data.database.entities.DeletedItemEntity
import com.wyldsoft.notes.data.database.entities.FolderEntity

class FolderRepository(
    private val folderDao: FolderDao,
    private val deletedItemDao: DeletedItemDao? = null
) {
    companion object {
        private const val TAG = "FolderRepository"
    }

    suspend fun createFolder(name: String, parentFolderId: String): FolderEntity {
        Log.d(TAG, "createFolder name=$name parentFolderId=$parentFolderId")
        val now = System.currentTimeMillis()
        val folder = FolderEntity(
            id = NanoIdUtils.randomNanoId(),
            name = name,
            parentFolderId = parentFolderId,
            createdAt = now,
            modifiedAt = now
        )
        folderDao.insert(folder)
        return folder
    }

    suspend fun getChildFolders(parentFolderId: String): List<FolderEntity> {
        Log.d(TAG, "getChildFolders parentFolderId=$parentFolderId")
        return folderDao.getChildFolders(parentFolderId)
    }

    suspend fun getById(id: String): FolderEntity? {
        Log.d(TAG, "getById id=$id")
        return folderDao.getById(id)
    }

    suspend fun getBreadcrumbPath(folderId: String): List<FolderEntity> {
        Log.d(TAG, "getBreadcrumbPath folderId=$folderId")
        val path = mutableListOf<FolderEntity>()
        var currentId: String? = folderId
        while (currentId != null) {
            val folder = folderDao.getById(currentId) ?: break
            path.add(0, folder)
            currentId = folder.parentFolderId
        }
        return path
    }

    suspend fun renameFolder(id: String, newName: String) {
        Log.d(TAG, "renameFolder id=$id newName=$newName")
        folderDao.renameFolder(id, newName, System.currentTimeMillis())
    }

    suspend fun moveFolder(id: String, newParentId: String) {
        Log.d(TAG, "moveFolder id=$id newParentId=$newParentId")
        folderDao.moveFolder(id, newParentId, null, System.currentTimeMillis())
    }

    suspend fun moveToTrash(id: String) {
        Log.d(TAG, "moveToTrash id=$id")
        val folder = folderDao.getById(id) ?: return
        val originalParent = folder.parentFolderId ?: FolderEntity.ROOT_ID
        folderDao.moveFolder(id, FolderEntity.TRASH_ID, originalParent, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(id: String) {
        Log.d(TAG, "restoreFromTrash id=$id")
        val folder = folderDao.getById(id) ?: return
        val destination = folder.trashedFromId ?: FolderEntity.ROOT_ID
        // Verify destination still exists; fall back to root if not
        val destExists = folderDao.getById(destination) != null
        val finalDest = if (destExists) destination else FolderEntity.ROOT_ID
        folderDao.moveFolder(id, finalDest, null, System.currentTimeMillis())
    }

    suspend fun getFoldersInTrash(): List<FolderEntity> {
        Log.d(TAG, "getFoldersInTrash")
        return folderDao.getFoldersInTrash()
    }

    suspend fun permanentlyDelete(folder: FolderEntity) {
        Log.d(TAG, "permanentlyDelete id=${folder.id}")
        folderDao.deleteById(folder.id)
        deletedItemDao?.insert(
            DeletedItemEntity(
                entityId = folder.id,
                entityType = "folder",
                deletedAt = System.currentTimeMillis(),
                originalParentId = folder.trashedFromId
            )
        )
    }

    suspend fun getAllFolders(): List<FolderEntity> {
        Log.d(TAG, "getAllFolders")
        return folderDao.getAllFolderEntities()
    }
}
