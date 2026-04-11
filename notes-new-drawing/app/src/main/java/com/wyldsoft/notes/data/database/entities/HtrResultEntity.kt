package com.wyldsoft.notes.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "htr_results",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId")]
)
data class HtrResultEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val text: String,
    val confidence: Float,
    val shapeIds: String,       // JSON array of contributing shape IDs
    val boundingLeft: Float,    // note-space bounding box for scroll navigation
    val boundingTop: Float,
    val boundingRight: Float,
    val boundingBottom: Float,
    val timestamp: Long
)
