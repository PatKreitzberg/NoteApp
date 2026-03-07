package com.wyldsoft.notes.data.database.dao

import androidx.room.*
import com.wyldsoft.notes.data.database.entities.RecognizedSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecognizedSegmentDao {

    @Query("SELECT * FROM recognized_segments WHERE noteId = :noteId ORDER BY lineNumber")
    fun getSegmentsForNote(noteId: String): Flow<List<RecognizedSegmentEntity>>

    @Query("SELECT * FROM recognized_segments WHERE noteId = :noteId ORDER BY lineNumber")
    suspend fun getSegmentsForNoteOnce(noteId: String): List<RecognizedSegmentEntity>

    @Query("SELECT * FROM recognized_segments WHERE recognizedText LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun searchText(query: String): List<RecognizedSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<RecognizedSegmentEntity>)

    @Query("DELETE FROM recognized_segments WHERE noteId = :noteId")
    suspend fun deleteAllForNote(noteId: String)

    @Query("DELETE FROM recognized_segments WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
