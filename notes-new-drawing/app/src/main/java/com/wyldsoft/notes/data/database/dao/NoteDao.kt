package com.wyldsoft.notes.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wyldsoft.notes.data.database.entities.NoteEntity

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE parentNotebookId = :notebookId ORDER BY createdAt")
    suspend fun getByNotebook(notebookId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE folderId = :folderId AND parentNotebookId IS NULL ORDER BY modifiedAt DESC")
    suspend fun getStandaloneByFolder(folderId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("UPDATE notes SET viewportScale = :scale, viewportOffsetX = :scrollX, viewportOffsetY = :scrollY, modifiedAt = :modifiedAt WHERE id = :noteId")
    suspend fun updateViewport(noteId: String, scale: Float, scrollX: Float, scrollY: Float, modifiedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isPaginationEnabled = :enabled, modifiedAt = :modifiedAt WHERE id = :noteId")
    suspend fun updatePagination(noteId: String, enabled: Boolean, modifiedAt: Long = System.currentTimeMillis())
}
