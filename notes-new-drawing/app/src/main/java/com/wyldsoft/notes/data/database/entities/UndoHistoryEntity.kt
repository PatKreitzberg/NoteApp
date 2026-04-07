package com.wyldsoft.notes.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "undo_history")
data class UndoHistoryEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val actionType: String,    // "DRAW" | "ERASE" | "MOVE"
    val isUndoStack: Boolean,  // true = undo stack, false = redo stack
    val sequenceNumber: Long,  // assigned at recordAction time; preserved when moving between stacks
    val shapesJson: String,    // serialized shape data for DRAW/ERASE; shape entity IDs for MOVE
    val dNoteX: Float = 0f,
    val dNoteY: Float = 0f
)
