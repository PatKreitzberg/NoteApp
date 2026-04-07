package com.wyldsoft.notes.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wyldsoft.notes.data.database.entities.UndoHistoryEntity

@Dao
interface UndoHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UndoHistoryEntity)

    @Query("SELECT * FROM undo_history WHERE noteId = :noteId")
    suspend fun getForNote(noteId: String): List<UndoHistoryEntity>

    @Query("UPDATE undo_history SET isUndoStack = :isUndoStack WHERE id = :id")
    suspend fun updateStackType(id: String, isUndoStack: Boolean)

    @Query("DELETE FROM undo_history WHERE noteId = :noteId AND isUndoStack = 0")
    suspend fun clearRedoStack(noteId: String)

    @Query("DELETE FROM undo_history WHERE noteId = :noteId")
    suspend fun clearAll(noteId: String)
}
