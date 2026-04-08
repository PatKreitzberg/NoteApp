# Template System Implementation Plan

## Context
The app already stores `paperTemplate: String` and `paperSize: String` on `NoteEntity`, and `isPaginationEnabled` on `NoteEntity`, but nothing renders them. `NotebookEntity` only has a generic `settings: String = "{}"` JSON blob with no typed fields. The goal is to:
- Add real template rendering (BLANK / GRID / COLLEGE_RULED / WIDE_RULED) to the canvas background
- Add notebook-level default settings (template + pagination) as real DB columns
- Add note-level "Override notebook settings" checkbox so individual notes can deviate from notebook defaults
- Both notebook defaults and note overrides live in the `EditorSettingsPanel` (single UI location)

## Files to Create
| File | Purpose |
|------|---------|
| `domain/models/PaperTemplate.kt` | Enum: BLANK, GRID, COLLEGE_RULED, WIDE_RULED |
| `rendering/TemplateRenderer.kt` | Draws template lines/grid onto a canvas in viewport space |

## Files to Modify
| File | Changes |
|------|---------|
| `data/database/entities/NotebookEntity.kt` | Add `template: String = "BLANK"`, `isPaginationEnabled: Boolean = false` columns |
| `data/database/entities/NoteEntity.kt` | Add `overrideNotebookSettings: Boolean = false` column; keep existing `paperTemplate`, `isPaginationEnabled` |
| `data/database/NotesDatabase.kt` | Bump version 5→6, add `MIGRATION_5_6` |
| `rendering/DrawingPipeline.kt` | Add `templateRenderer`, call it after white fill, before shapes |
| `editor/EditorState.kt` | Add `effectiveTemplate: StateFlow<PaperTemplate>`, `overrideNotebookSettings: StateFlow<Boolean>` |
| `editor/EditorSettingsPanel.kt` | Add notebook defaults section + note override section with checkbox |
| Any domain mappers / repositories that map Note/Notebook entities | Propagate new fields |

---

## Step-by-Step Implementation

### 1. PaperTemplate enum
Create `app/src/main/java/com/wyldsoft/notes/domain/models/PaperTemplate.kt`:
```kotlin
enum class PaperTemplate {
    BLANK, GRID, COLLEGE_RULED, WIDE_RULED;

    companion object {
        fun fromString(s: String) = entries.find { it.name == s } ?: BLANK
    }
}
```

### 2. Database migration (version 5 → 6)
Add to `NotebookEntity`:
```kotlin
val template: String = "BLANK",
val isPaginationEnabled: Boolean = false,
```
Add to `NoteEntity`:
```kotlin
val overrideNotebookSettings: Boolean = false,
```
Migration SQL (add to `NotesDatabase.kt`):
```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE notebook ADD COLUMN template TEXT NOT NULL DEFAULT 'BLANK'")
        database.execSQL("ALTER TABLE notebook ADD COLUMN isPaginationEnabled INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE note ADD COLUMN overrideNotebookSettings INTEGER NOT NULL DEFAULT 0")
    }
}
```
Add `MIGRATION_5_6` to the `Room.databaseBuilder` migration list.

### 3. TemplateRenderer
Create `app/src/main/java/com/wyldsoft/notes/rendering/TemplateRenderer.kt`:

**Key design**: Template lines are defined in note-space (mm → note pixels), then projected into viewport-space using `ViewportManager`. This ensures they scale correctly on zoom.

```kotlin
class TemplateRenderer(private val density: Float) {
    companion object {
        private const val TAG = "TemplateRenderer"
        // mm to note-pixels conversion: 1mm = density * 160 / 25.4 ≈ density * 6.299 px
        private fun mmToNotePx(mm: Float, density: Float) = mm * density * 6.299f

        val GRID_SPACING_MM = 5f           // standard graph paper 5mm
        val COLLEGE_RULED_MM = 7.127f      // 9/32 inch
        val WIDE_RULED_MM = 8.731f         // 11/32 inch
    }
    
    // line paint: light grey, hairline scaled by viewport
    private val linePaint = Paint().apply {
        color = Color.argb(80, 100, 100, 100)  // semi-transparent grey
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }
    private val marginPaint = Paint().apply {
        color = Color.argb(60, 200, 100, 100)  // reddish margin line
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
}
```

**`drawTemplate(canvas, template, viewportManager, pageRects)`** — called from DrawingPipeline:
- If `template == BLANK`: return immediately
- For GRID: draw vertical + horizontal lines at `GRID_SPACING_MM` intervals
- For COLLEGE_RULED / WIDE_RULED: draw horizontal lines at appropriate spacing, plus a left margin line (1.25" = 31.75mm from left edge of page)
- For pagination mode: `pageRects` is list of page-space RectF; clip drawing to each page rect and skip gaps
- For no-pagination: single "infinite" page starting at `(0, 0)` in note-space

**Line calculation algorithm** (for horizontal ruled lines, no pagination):
```
spacingNotePixels = mmToNotePx(spacing, density)
// find first line below top of visible note-space
noteTop = viewportManager.viewportToNote(0f, 0f).y
noteBottom = viewportManager.viewportToNote(0f, canvasHeight.toFloat()).y
firstLineY = ceil(noteTop / spacingNotePixels) * spacingNotePixels
// iterate lines, convert each to viewport space
y = firstLineY
while y < noteBottom:
    vy = viewportManager.noteToViewport(0f, y).y
    canvas.drawLine(0f, vy, canvasWidth, vy, paint)
    y += spacingNotePixels
```

**Pagination mode**: For each page rect in note-space:
- Calculate `pageTopNoteY`, `pageBottomNoteY`
- Only draw lines where `y` falls within the page boundaries
- Clip canvas to page viewport rect before drawing lines in that page

### 4. DrawingPipeline integration
In `recreateBitmapFromShapes()`, after `canvas.drawColor(Color.WHITE)` and before the shape loop, add:
```kotlin
templateRenderer.drawTemplate(canvas, currentTemplate, viewportManager, 
    paginationManager?.let { pm -> (0 until pm.pageCount).map { i ->
        RectF(0f, pm.pageTopY(i), pm.pageWidth, pm.pageBottomY(i))
    }})
```
Add `var currentTemplate: PaperTemplate = PaperTemplate.BLANK` field to `DrawingPipeline`. Wire updates from `EditorState`.

### 5. EditorState additions
```kotlin
// Notebook default
val notebookTemplate = MutableStateFlow(PaperTemplate.BLANK)
val notebookPaginationEnabled = MutableStateFlow(false)

// Note override
val noteTemplate = MutableStateFlow(PaperTemplate.BLANK)
val overrideNotebookSettings = MutableStateFlow(false)

// Effective values used by rendering
val effectiveTemplate: StateFlow<PaperTemplate> = combine(
    overrideNotebookSettings, noteTemplate, notebookTemplate
) { override, note, notebook -> if (override) note else notebook }.stateIn(...)

val effectivePagination: StateFlow<Boolean> = combine(
    overrideNotebookSettings, paginationEnabled, notebookPaginationEnabled
) { override, note, notebook -> if (override) note else notebook }.stateIn(...)
```
Load both notebook and note settings when a note is opened. Persist changes through repositories.

### 6. EditorSettingsPanel UI
Restructure `EditorSettingsPanel.kt` into two sections:

**Section A — Notebook Defaults** (always visible):
- Label: "Notebook Defaults"
- Template dropdown: BLANK / GRID / COLLEGE_RULED / WIDE_RULED (affects all notes that don't override)
- Pagination toggle

**Section B — Note Settings**:
- "Override notebook settings" checkbox
- When checked, show:
  - Note template dropdown
  - Note pagination toggle

Use `DropdownMenu` + `DropdownMenuItem` for template selection (similar to existing Compose patterns in the codebase).

---

## Effective Settings Resolution (summary)

| Override checkbox | Template used | Pagination used |
|-------------------|--------------|----------------|
| unchecked (default) | Notebook's template | Notebook's pagination |
| checked | Note's own template | Note's own pagination |

---

## Template Line Measurements

| Template | Horizontal spacing | Notes |
|----------|--------------------|-------|
| GRID | 5mm | Both H and V lines |
| COLLEGE_RULED | 7.127mm (9/32") | H lines only + left margin at 31.75mm (1.25") |
| WIDE_RULED | 8.731mm (11/32") | H lines only + left margin at 31.75mm (1.25") |

---

## Verification Steps
1. Create a new note → template defaults to notebook default (BLANK)
2. Open EditorSettingsPanel → change notebook template to GRID → all notes in notebook without override show grid
3. Open EditorSettingsPanel → check "Override notebook settings" → set note template to COLLEGE_RULED → only this note shows ruled lines
4. Enable pagination → template restarts at top of each page; gaps between pages have no template lines
5. Zoom in/out → template lines scale correctly (because they're calculated in note-space)
6. Create note with WIDE_RULED → horizontal lines visible with reddish left margin line
7. Rotate device / change orientation → template lines recomputed correctly on next `recreateBitmapFromShapes` call
8. Uncheck "Override notebook settings" → note reverts to notebook's template instantly
9. DB migration: existing users keep BLANK template and their existing pagination setting

---

## Also: Save this plan to MD-FILES/
During implementation, write this plan to:
`MD-FILES/implementation-plans/template-system.md`
