package com.wyldsoft.notes.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wyldsoft.notes.data.database.entities.FolderEntity

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentId ORDER BY name")
    suspend fun getChildFolders(parentId: String): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    // Sync methods
    @Query("SELECT * FROM folders WHERE modifiedAt > :timestamp")
    suspend fun getFoldersModifiedAfter(timestamp: Long): List<FolderEntity>

    @Query("SELECT * FROM folders")
    suspend fun getAllFolderEntities(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolder(id: String): FolderEntity?
}
