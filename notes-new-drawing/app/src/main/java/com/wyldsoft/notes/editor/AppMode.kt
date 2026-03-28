package com.wyldsoft.notes.editor

/**
 * The set of major application modes. The app is always in exactly one mode.
 * DRAWING: stylus and drawing are active; pen input is enabled.
 */
enum class AppMode {
    DRAWING,
    SELECTION,
    OPENMENU,
    HOME
}
