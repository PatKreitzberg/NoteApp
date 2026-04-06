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
