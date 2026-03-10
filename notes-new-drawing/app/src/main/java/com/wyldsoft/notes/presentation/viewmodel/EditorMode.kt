package com.wyldsoft.notes.presentation.viewmodel

/**
 * Represents the current editor mode. The editor is always in exactly one mode.
 * Draw mode is the default.
 *
 * To add a new mode:
 * 1. Add a new subclass here
 * 2. Add enter/exit logic in EditorViewModel.enterMode() / exitMode()
 * 3. Add input routing in OnyxStylusHandler / GenericStylusHandler
 * 4. Add a toolbar tab in Toolbar.kt
 */
sealed class EditorMode {
    data class Draw(val drawTool: DrawTool = DrawTool.PEN) : EditorMode()
    data object Select : EditorMode()
    data object Text : EditorMode()
}

enum class DrawTool {
    PEN,
    ERASER,
    GEOMETRY
}
