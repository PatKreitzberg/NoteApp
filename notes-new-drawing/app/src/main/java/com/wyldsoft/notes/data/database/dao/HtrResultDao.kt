package com.wyldsoft.notes.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wyldsoft.notes.data.database.entities.HtrResultEntity

data class HtrSearchRow(
    val id: String,
    val noteId: String,
    val text: String,
    val boundingTop: Float,
    val notebookId: String,
    val notebookName: String
)

data class TextShapeSearchRow(
    val shapeId: String,
    val noteId: String,
    val text: String,
    val points: String,
    val notebookId: String,
    val notebookName: String
)

@Dao
interface HtrResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HtrResultEntity)

    @Query("SELECT * FROM htr_results WHERE noteId = :noteId")
    suspend fun getByNoteId(noteId: String): List<HtrResultEntity>

    @Query("DELETE FROM htr_results WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("""
        SELECT htr.id, htr.noteId, htr.text, htr.boundingTop,
               n.id AS notebookId, n.name AS notebookName
        FROM htr_results htr
        JOIN notes note ON htr.noteId = note.id
        JOIN notebooks n ON note.parentNotebookId = n.id
        WHERE htr.text LIKE '%' || :query || '%'
          AND n.folderId != 'trash'
    """)
    suspend fun searchAcrossNotes(query: String): List<HtrSearchRow>

    @Query("UPDATE htr_results SET boundingLeft=:l, boundingTop=:t, boundingRight=:r, boundingBottom=:b WHERE id=:id")
    suspend fun updateBoundingBox(id: String, l: Float, t: Float, r: Float, b: Float)
}
