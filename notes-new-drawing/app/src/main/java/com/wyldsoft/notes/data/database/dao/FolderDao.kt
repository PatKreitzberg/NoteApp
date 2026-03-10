package com.wyldsoft.notes.data.database.dao

import androidx.room.*
import com.wyldsoft.notes.data.database.entities.FolderEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.OnConflictStrategy

@Dao
interface FolderDao {
    
    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolder(folderId: String): FolderEntity?
    
    @Query("SELECT * FROM folders WHERE parentFolderId = :parentId")
    fun getSubfolders(parentId: String?): Flow<List<FolderEntity>>
    
    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL LIMIT 1")
    suspend fun getRootFolder(): FolderEntity?
    
    @Insert
    suspend fun insert(folder: FolderEntity)
    
    @Update
    suspend fun update(folder: FolderEntity)
    
    @Delete
    suspend fun delete(folder: FolderEntity)
    
    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteById(folderId: String)
    
    // Get path from folder to root for breadcrumb
    @Query("""
        WITH RECURSIVE folder_path AS (
            SELECT * FROM folders WHERE id = :folderId
            UNION ALL
            SELECT f.* FROM folders f
            JOIN folder_path fp ON f.id = fp.parentFolderId
        )
        SELECT * FROM folder_path ORDER BY parentFolderId
    """)
    suspend fun getFolderPath(folderId: String): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE modifiedAt > :timestamp")
    suspend fun getFoldersModifiedAfter(timestamp: Long): List<FolderEntity>

    @Query("SELECT * FROM folders")
    suspend fun getAllFolderEntities(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentId")
    suspend fun getSubfoldersOnce(parentId: String): List<FolderEntity>

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersOnce(): List<FolderEntity>
}