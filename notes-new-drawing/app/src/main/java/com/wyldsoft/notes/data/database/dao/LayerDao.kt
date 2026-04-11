package com.wyldsoft.notes.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wyldsoft.notes.data.database.entities.LayerEntity

@Dao
interface LayerDao {

    @Query("SELECT * FROM layers WHERE noteId = :noteId ORDER BY position ASC")
    suspend fun getLayersForNote(noteId: String): List<LayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(layer: LayerEntity)

    @Update
    suspend fun update(layer: LayerEntity)

    @Delete
    suspend fun delete(layer: LayerEntity)

    @Query("DELETE FROM layers WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("SELECT MAX(position) FROM layers WHERE noteId = :noteId")
    suspend fun getMaxPositionForNote(noteId: String): Int?
}
