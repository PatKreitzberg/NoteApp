package com.wyldsoft.notes.undoredo

import kotlinx.serialization.Serializable

/**
 * Serializable snapshot of a shape used to persist undo/redo history.
 * Mirrors the fields of ShapeEntity (minus noteId) so shapes can be
 * fully reconstructed from undo history after a note is reloaded.
 */
@Serializable
data class StoredShapeData(
    val id: String,
    val type: String,
    val penType: String,
    val strokeColor: Int,
    val strokeWidth: Float,
    val points: String,
    val pressure: String,
    val tiltX: String,
    val tiltY: String,
    val pointTimestamps: String,
    val layer: Int = 1
)
