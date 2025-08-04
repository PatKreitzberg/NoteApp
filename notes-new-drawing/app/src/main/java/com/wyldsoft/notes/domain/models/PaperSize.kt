package com.wyldsoft.notes.domain.models

/**
 * Enum representing different paper sizes for pagination
 */
enum class PaperSize(
    val displayName: String,
    val aspectRatio: Float // height/width ratio
) {
    LETTER("Letter (8.5\" × 11\")", 11f / 8.5f),
    A4("A4 (210mm × 297mm)", 297f / 210f),
    LEGAL("Legal (8.5\" × 14\")", 14f / 8.5f);

    companion object {
        fun fromString(value: String): PaperSize {
            return values().find { it.name == value } ?: LETTER
        }
    }
}