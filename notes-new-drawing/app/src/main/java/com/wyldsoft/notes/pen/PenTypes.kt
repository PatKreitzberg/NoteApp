package com.wyldsoft.notes.pen

/**
 * Enumerates all available pen types. Each type maps to a different Shape subclass
 * via ShapeFactory and a different Onyx SDK stroke style via PenProfile.
 * The displayName is shown in the pen-picker UI.
 */
enum class PenType(val displayName: String) {
    BALLPEN("Ball Pen"),
    FOUNTAIN("Fountain Pen"),
    MARKER("Marker"),
    PENCIL("Pencil"),
    CHARCOAL("Charcoal"),
    CHARCOAL_V2("Charcoal V2"),
    NEO_BRUSH("Neo Brush"),
    DASH("Dash Pen")
}