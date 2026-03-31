package com.wyldsoft.notes.data.database.repository

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.data.database.dao.FolderDao
import com.wyldsoft.notes.data.database.entities.FolderEntity

class FolderRepository(private val folderDao: FolderDao) {
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
}
