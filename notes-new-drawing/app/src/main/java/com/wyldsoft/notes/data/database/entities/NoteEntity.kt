package com.wyldsoft.notes.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    indices = [
        Index(value = ["parentNotebookId"]),
        Index(value = ["folderId"])
    ]
)
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val title: String = "Untitled",
    val parentNotebookId: String? = null,
    val folderId: String? = null,
    val settings: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val viewportScale: Float = 1.0f,
    @ColumnInfo(name = "viewportOffsetX")
    val viewportScrollX: Float = 0f,
    @ColumnInfo(name = "viewportOffsetY")
    val viewportScrollY: Float = 0f,
    val isPaginationEnabled: Boolean = true,
    val paperSize: String = "LETTER",
    val paperTemplate: String = "BLANK",
    val pdfPath: String? = null,
    val pdfPageCount: Int = 0,
    val pdfPageAspectRatio: Float = 0f
)
