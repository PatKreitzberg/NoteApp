package com.wyldsoft.notes.data.database.dao

import androidx.room.*
import com.wyldsoft.notes.data.database.entities.NoteEntity
import com.wyldsoft.notes.data.database.entities.NoteNotebookCrossRef
import kotlinx.coroutines.flow.Flow
import androidx.room.OnConflictStrategy

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNote(noteId: String): NoteEntity
    
    @Query("SELECT * FROM notes WHERE id = :noteId")
    fun getNoteFlow(noteId: String): Flow<NoteEntity>
    
    @Query("SELECT * FROM notes WHERE folderId = :folderId AND parentNotebookId IS NULL")
    fun getLooseNotesInFolder(folderId: String): Flow<List<NoteEntity>>
    
    @Insert
    suspend fun insert(note: NoteEntity)
    
    @Update
    suspend fun update(note: NoteEntity)
    
    @Delete
    suspend fun delete(note: NoteEntity)
    
    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteById(noteId: String)
    
    // Cross-reference operations
    @Insert
    suspend fun insertNoteNotebookCrossRef(crossRef: NoteNotebookCrossRef)
    
    @Delete
    suspend fun deleteNoteNotebookCrossRef(crossRef: NoteNotebookCrossRef)
    
    @Query("SELECT notebookId FROM note_notebook_cross_ref WHERE noteId = :noteId")
    suspend fun getNotebooksForNote(noteId: String): List<String>

    @Query("SELECT * FROM notes WHERE modifiedAt > :timestamp")
    suspend fun getNotesModifiedAfter(timestamp: Long): List<NoteEntity>

    @Query("SELECT * FROM notes")
    suspend fun getAllNoteEntities(): List<NoteEntity>

    @Query("SELECT * FROM note_notebook_cross_ref WHERE noteId = :noteId")
    suspend fun getCrossRefsForNote(noteId: String): List<NoteNotebookCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCrossRef(crossRef: NoteNotebookCrossRef)

    @Query("DELETE FROM note_notebook_cross_ref WHERE noteId = :noteId")
    suspend fun deleteCrossRefsForNote(noteId: String)
}