package com.wyldsoft.notes.data.database.entities

import androidx.room.ColumnInfo
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
    @ColumnInfo(name = "viewportOffsetX") val viewportScrollX: Float = 0f,
    @ColumnInfo(name = "viewportOffsetY") val viewportScrollY: Float = 0f,
    val isPaginationEnabled: Boolean = true,
    val paperSize: String = "LETTER",
    val paperTemplate: String = "BLANK",
    val pdfPath: String? = null,
    val pdfPageCount: Int = 0,
    val pdfPageAspectRatio: Float = 0f
)