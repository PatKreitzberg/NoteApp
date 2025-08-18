package com.wyldsoft.notes.domain.models

import java.util.UUID

data class Note(
    val id: String = "default",
    val title: String = "Untitled",
    val shapes: MutableList<Shape> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val template: Template? = null,
    val viewportScale: Float = 1.0f,
    val viewportOffsetX: Float = 0f,
    val viewportOffsetY: Float = 0f,
    val isPaginationEnabled: Boolean = true,
    val paperSize: String = "LETTER"
)

data class Template(
    val type: TemplateType,
    val spacing: Float = 20f
)

enum class TemplateType {
    BLANK,
    GRID,
    RULED,
    DOTTED
}