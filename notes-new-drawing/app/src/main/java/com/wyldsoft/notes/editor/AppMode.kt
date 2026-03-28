package com.wyldsoft.notes.editor

/**
 * The set of major application modes. The app is always in exactly one mode.
 *
 * HOME: No note open. User browses folders, notes, and notebooks.
 * DRAWING: Stylus and drawing are active; pen input is enabled. Default mode when a note is open.
 * SELECTION: (Not yet implemented) Stylus selects shapes on canvas.
 * TEXT: (Not yet implemented) System keyboard text entry.
 * SETTINGS: For when a settings dialog or dropdown is open
 */
enum class AppMode {
    HOME,
    DRAWING,
    SELECTION,
    TEXT,
    SETTINGS
}
