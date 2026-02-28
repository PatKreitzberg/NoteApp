package com.wyldsoft.notes.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentNotebookId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("parentNotebookId")]
)
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val title: String = "Untitled",
    val parentNotebookId: String? = null, // The primary notebook this note belongs to
    val folderId: String? = null, // For loose notes in a folder
    val settings: String = "{}", // JSON string for note settings
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val viewportScale: Float = 1.0f,
    val viewportOffsetX: Float = 0f,
    val viewportOffsetY: Float = 0f,
    val isPaginationEnabled: Boolean = true,
    val paperSize: String = "LETTER",
    val paperTemplate: String = "BLANK"
)