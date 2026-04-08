package com.wyldsoft.notes.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wyldsoft.notes.data.database.entities.NotebookEntity

@Dao
interface NotebookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notebook: NotebookEntity)

    @Query("SELECT * FROM notebooks WHERE folderId = :folderId ORDER BY name")
    suspend fun getByFolder(folderId: String): List<NotebookEntity>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getById(id: String): NotebookEntity?

    @Update
    suspend fun update(notebook: NotebookEntity)

    @Delete
    suspend fun delete(notebook: NotebookEntity)

    @Query("SELECT * FROM notebooks WHERE folderId = 'trash' ORDER BY modifiedAt DESC")
    suspend fun getNotebooksInTrash(): List<NotebookEntity>

    @Query("UPDATE notebooks SET folderId = :newFolderId, trashedFromId = :trashedFrom, modifiedAt = :now WHERE id = :id")
    suspend fun moveNotebook(id: String, newFolderId: String, trashedFrom: String?, now: Long)

    @Query("UPDATE notebooks SET name = :name, modifiedAt = :now WHERE id = :id")
    suspend fun renameNotebook(id: String, name: String, now: Long)

    @Query("UPDATE notebooks SET template = :template, modifiedAt = :now WHERE id = :id")
    suspend fun updateTemplate(id: String, template: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE notebooks SET isPaginationEnabled = :enabled, modifiedAt = :now WHERE id = :id")
    suspend fun updatePagination(id: String, enabled: Boolean, now: Long = System.currentTimeMillis())

    // Sync methods
    @Query("SELECT * FROM notebooks WHERE modifiedAt > :timestamp")
    suspend fun getNotebooksModifiedAfter(timestamp: Long): List<NotebookEntity>

    @Query("SELECT * FROM notebooks")
    suspend fun getAllNotebookEntities(): List<NotebookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotebook(notebook: NotebookEntity)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNotebook(id: String): NotebookEntity?
}
