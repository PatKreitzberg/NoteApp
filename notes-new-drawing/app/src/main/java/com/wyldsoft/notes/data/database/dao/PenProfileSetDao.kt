package com.wyldsoft.notes.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wyldsoft.notes.data.database.entities.PenProfileSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PenProfileSetDao {
    @Query("SELECT * FROM pen_profile_sets ORDER BY name ASC")
    fun getAllFlow(): Flow<List<PenProfileSetEntity>>

    @Query("SELECT * FROM pen_profile_sets ORDER BY name ASC")
    suspend fun getAll(): List<PenProfileSetEntity>

    @Query("SELECT * FROM pen_profile_sets WHERE id = :id")
    suspend fun getById(id: String): PenProfileSetEntity?

    @Query("SELECT COUNT(*) FROM pen_profile_sets")
    suspend fun getCount(): Int

    @Query("SELECT * FROM pen_profile_sets WHERE updatedAt > :since ORDER BY name ASC")
    suspend fun getModifiedAfter(since: Long): List<PenProfileSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(set: PenProfileSetEntity)

    @Update
    suspend fun update(set: PenProfileSetEntity)

    @Query("DELETE FROM pen_profile_sets WHERE id = :id")
    suspend fun deleteById(id: String)
}
