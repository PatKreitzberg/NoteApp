package com.wyldsoft.notes.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wyldsoft.notes.data.database.entities.ShapeEntity

@Dao
interface ShapeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shape: ShapeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shapes: List<ShapeEntity>)

    @Query("SELECT * FROM shapes WHERE noteId = :noteId")
    suspend fun getByNoteId(noteId: String): List<ShapeEntity>

    @Query("DELETE FROM shapes WHERE id = :shapeId")
    suspend fun deleteById(shapeId: String)

    @Query("DELETE FROM shapes WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    // Sync method aliases
    @Query("SELECT * FROM shapes WHERE noteId = :noteId ORDER BY layer, timestamp")
    suspend fun getShapesForNoteOnce(noteId: String): List<ShapeEntity>

    @Query("DELETE FROM shapes WHERE noteId = :noteId")
    suspend fun deleteAllForNote(noteId: String)
}
