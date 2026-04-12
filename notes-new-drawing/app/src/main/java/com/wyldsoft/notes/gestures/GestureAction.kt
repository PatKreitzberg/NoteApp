package com.wyldsoft.notes.gestures

enum class GestureAction {
    NONE,
    RESET_VIEWPORT,
    UNDO,
    REDO,
    ENTER_SELECTION_MODE,
    TOGGLE_ERASER_MODE,
    NEXT_NOTE,
    PREVIOUS_NOTE,
    FULL_SCREEN_REFRESH;

    fun displayName(): String = when (this) {
        NONE -> "None"
        RESET_VIEWPORT -> "Reset Viewport"
        UNDO -> "Undo"
        REDO -> "Redo"
        ENTER_SELECTION_MODE -> "Enter Selection Mode"
        TOGGLE_ERASER_MODE -> "Enter/Exit Eraser Mode"
        NEXT_NOTE -> "Next Note"
        PREVIOUS_NOTE -> "Previous Note"
        FULL_SCREEN_REFRESH -> "Full Screen Refresh"
    }
}
