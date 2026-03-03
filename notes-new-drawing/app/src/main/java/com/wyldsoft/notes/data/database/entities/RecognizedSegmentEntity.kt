package com.wyldsoft.notes.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.wyldsoft.notes.data.database.converters.Converters

@Entity(
    tableName = "recognized_segments",
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
@TypeConverters(Converters::class)
data class RecognizedSegmentEntity(
    @PrimaryKey
    val id: String,
    val noteId: String,
    val shapeIds: List<String>,
    val recognizedText: String,
    val confidence: Float,
    val lineNumber: Int,
    val boundingBoxLeft: Float,
    val boundingBoxTop: Float,
    val boundingBoxRight: Float,
    val boundingBoxBottom: Float,
    val timestamp: Long = System.currentTimeMillis()
)
