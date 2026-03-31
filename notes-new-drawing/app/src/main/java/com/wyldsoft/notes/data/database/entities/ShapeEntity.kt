package com.wyldsoft.notes.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shapes",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["noteId"])]
)
data class ShapeEntity(
    @PrimaryKey
    val id: String,
    val noteId: String,
    val type: String,
    val points: String,
    val strokeWidth: Float,
    val strokeColor: Int,
    val penType: String,
    val pressure: String,
    val tiltX: String,
    val tiltY: String,
    val pointTimestamps: String,
    val timestamp: Long = System.currentTimeMillis(),
    val text: String? = null,
    val fontSize: Float = 32f,
    val fontFamily: String = "sans-serif",
    val layer: Int = 1
)
