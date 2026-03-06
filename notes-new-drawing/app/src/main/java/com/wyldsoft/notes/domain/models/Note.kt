package com.wyldsoft.notes.domain.models

import java.util.UUID

data class Note(
    val id: String = "default",
    val title: String = "Untitled",
    val shapes: MutableList<Shape> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val paperTemplate: PaperTemplate = PaperTemplate.BLANK,
    val viewportScale: Float = 1.0f,
    val viewportScrollX: Float = 0f,
    val viewportScrollY: Float = 0f,
    val isPaginationEnabled: Boolean = true,
    val paperSize: String = "LETTER"
)

enum class PaperTemplate(val displayName: String) {
    BLANK("Blank"),
    GRID("Grid"),
    RULED("Ruled (College)");

    companion object {
        fun fromString(value: String): PaperTemplate {
            return entries.find { it.name == value } ?: BLANK
        }
    }
}