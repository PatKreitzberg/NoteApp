package com.wyldsoft.notes.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "layers",
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
data class LayerEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val position: Int,       // 1, 2, 3, ... matches ShapeEntity.layer
    val name: String,        // "Layer 1", user-editable
    val visible: Boolean = true
)
