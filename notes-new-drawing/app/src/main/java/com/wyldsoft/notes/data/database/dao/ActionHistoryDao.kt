package com.wyldsoft.notes.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wyldsoft.notes.data.database.entities.ActionHistoryEntity

@Dao
interface ActionHistoryDao {

    @Query("SELECT * FROM action_history WHERE noteId = :noteId ORDER BY onUndoStack DESC, position ASC")
    suspend fun getActionsForNote(noteId: String): List<ActionHistoryEntity>

    @Query("DELETE FROM action_history WHERE noteId = :noteId")
    suspend fun deleteAllForNote(noteId: String)

    @Insert
    suspend fun insertAll(entities: List<ActionHistoryEntity>)
}
