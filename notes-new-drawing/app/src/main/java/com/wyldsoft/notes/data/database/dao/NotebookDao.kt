package com.wyldsoft.notes.data.database.dao

import androidx.room.*
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.database.entities.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    
    @Query("SELECT * FROM notebooks WHERE id = :notebookId")
    suspend fun getNotebook(notebookId: String): NotebookEntity?
    
    @Query("SELECT * FROM notebooks WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun getNotebooksByFolder(folderId: String): Flow<List<NotebookEntity>>
    
    @Insert
    suspend fun insert(notebook: NotebookEntity)
    
    @Update
    suspend fun update(notebook: NotebookEntity)
    
    @Delete
    suspend fun delete(notebook: NotebookEntity)
    
    @Query("DELETE FROM notebooks WHERE id = :notebookId")
    suspend fun deleteById(notebookId: String)
    
    @Transaction
    @Query("""
        SELECT n.* FROM notes n
        JOIN note_notebook_cross_ref cr ON n.id = cr.noteId
        WHERE cr.notebookId = :notebookId
        ORDER BY n.createdAt
    """)
    fun getNotesInNotebook(notebookId: String): Flow<List<NoteEntity>>
    
    @Query("""
        SELECT n.* FROM notes n
        JOIN note_notebook_cross_ref cr ON n.id = cr.noteId
        WHERE cr.notebookId = :notebookId
        ORDER BY n.createdAt
        LIMIT 1
    """)
    suspend fun getFirstNoteInNotebook(notebookId: String): NoteEntity?

    @Transaction
    @Query("""
        SELECT n.* FROM notes n
        JOIN note_notebook_cross_ref cr ON n.id = cr.noteId
        WHERE cr.notebookId = :notebookId
        ORDER BY n.createdAt
    """)
    suspend fun getNotesInNotebookOnce(notebookId: String): List<NoteEntity>
}