package com.wyldsoft.notes.models

enum class PaperTemplate {
    BLANK, GRID, COLLEGE_RULED, WIDE_RULED;

    fun displayName(): String = when (this) {
        BLANK -> "Blank"
        GRID -> "Grid"
        COLLEGE_RULED -> "College Ruled"
        WIDE_RULED -> "Wide Ruled"
    }

    companion object {
        fun fromString(s: String): PaperTemplate = entries.find { it.name == s } ?: BLANK
    }
}
