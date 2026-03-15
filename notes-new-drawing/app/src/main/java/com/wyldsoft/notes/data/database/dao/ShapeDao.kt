package com.wyldsoft.notes.data.database.dao

import androidx.room.*
import com.wyldsoft.notes.data.database.entities.ShapeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShapeDao {
    
    @Query("SELECT * FROM shapes WHERE noteId = :noteId ORDER BY layer, timestamp")
    fun getShapesForNote(noteId: String): Flow<List<ShapeEntity>>

    @Query("SELECT * FROM shapes WHERE noteId = :noteId ORDER BY layer, timestamp")
    suspend fun getShapesForNoteOnce(noteId: String): List<ShapeEntity>
    
    @Insert
    suspend fun insert(shape: ShapeEntity)
    
    @Insert
    suspend fun insertAll(shapes: List<ShapeEntity>)
    
    @Delete
    suspend fun delete(shape: ShapeEntity)
    
    @Query("DELETE FROM shapes WHERE id = :shapeId")
    suspend fun deleteById(shapeId: String)
    
    @Query("DELETE FROM shapes WHERE noteId = :noteId")
    suspend fun deleteAllForNote(noteId: String)
    
    @Update
    suspend fun update(shape: ShapeEntity)

    @Query("UPDATE shapes SET layer = :newLayer WHERE id IN (:shapeIds)")
    suspend fun updateShapesLayer(shapeIds: List<String>, newLayer: Int)
}