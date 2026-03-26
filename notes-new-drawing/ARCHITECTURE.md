# Notes App — Deep Architecture Reference

> Last updated: 2026-03-06
> Purpose: Human-readable guide to how the app works end-to-end, written to help
> developers (and AI assistants) understand, debug, and extend the codebase without
> having to re-read every file from scratch.

---

## Table of Contents

1. [Bird's-eye Overview](#1-birds-eye-overview)
2. [Coordinate Systems](#2-coordinate-systems)
3. [Startup Sequence](#3-startup-sequence)
4. [Drawing — Stroke Lifecycle](#4-drawing--stroke-lifecycle)
5. [Canvas Update & Screen Refresh](#5-canvas-update--screen-refresh)
6. [Erasing](#6-erasing)
7. [Viewport — Pan & Zoom](#7-viewport--pan--zoom)
8. [Enabling and Disabling Drawing](#8-enabling-and-disabling-drawing)
9. [Gestures (Finger Touch)](#9-gestures-finger-touch)
10. [Selection, Move, Scale, Rotate](#10-selection-move-scale-rotate)
11. [Undo / Redo](#11-undo--redo)
12. [Pagination & Paper Templates](#12-pagination--paper-templates)
13. [Data Layer — Persistence](#13-data-layer--persistence)
14. [UI Layer (Compose)](#14-ui-layer-compose)
15. [Observers in BaseDrawingActivity](#15-observers-in-basedrawingactivity)
16. [Note Navigation in a Notebook](#16-note-navigation-in-a-notebook)
17. [SDK Abstraction Layer](#17-sdk-abstraction-layer)
18. [Known Pain Points & Refactor Hints](#18-known-pain-points--refactor-hints)

---

## 1. Bird's-eye Overview

```
┌──────────────────────────────────────────────────────────────┐
│  Compose UI  (EditorView, Toolbar, StrokeOptionsPanel, etc.) │
│  State consumed via StateFlow / collectAsState()             │
└────────────────────────┬─────────────────────────────────────┘
                         │ calls / emits state
┌────────────────────────▼─────────────────────────────────────┐
│  EditorViewModel                                             │
│  • Owns: ViewportManager, SelectionManager, ActionManager    │
│  • Emits: uiState, currentPenProfile, viewportState, etc.   │
│  • Delegates persistence to NoteRepository                   │
└──────┬────────────────────────────────────┬──────────────────┘
       │ references                         │ observes
┌──────▼──────────────┐         ┌───────────▼──────────────────┐
│  BaseDrawingActivity│         │  NoteRepository (Room DB)    │
│  (template-method)  │         │  NoteDao, ShapeDao           │
│  OnyxDrawingActivity│         └──────────────────────────────┘
│  - onyxTouchHelper  │
│  - BitmapManager    │
│  - ShapesManager    │
│  - GestureHandler   │
└──────┬──────────────┘
       │ raw stylus events
┌──────▼──────────────────────────────────────┐
│  AbstractStylusHandler / OnyxStylusHandler  │
│  Onyx SDK RawInputCallback                  │
│  - DrawManager  (new strokes)               │
│  - EraseManager (hit-test + remove)         │
└──────┬──────────────────────────────────────┘
       │ bitmap ops
┌──────▼────────────────────────────────────────┐
│  BitmapManager                                │
│  - recreateBitmapFromShapes()                 │
│  - renderBitmapToScreen()  ──► SurfaceView    │
│  - PaginationRendererToScreenRequest          │
│      (page separators, e-ink EPD mode)        │
└───────────────────────────────────────────────┘
```

**Key insight:** There are two parallel representations of shapes:
- **Domain / DB layer**: `Shape` (PointF list, pressure list, metadata) stored in Room via `NoteRepository`.
- **SDK / in-memory layer**: `BaseShape` (Onyx SDK shape with `TouchPointList`) held in `ShapesManager`. This is what gets rendered.

`ShapesManager` is rebuilt from the DB shapes on every note open. Both representations must stay in sync.

---

## 2. Coordinate Systems

There are exactly two coordinate spaces and one manager to convert between them.

| Name | Description | Where used |
|------|-------------|------------|
| **NoteCoordinates** | Absolute position in the infinite note canvas. Origin (0,0) top-left. Never changes when you scroll/zoom. | Shapes stored in DB, `ShapesManager`, `SelectionManager` bounding boxes |
| **SurfaceViewCoordinates** | Pixel position on the physical screen surface. Depends on scroll and zoom. | Stylus raw input from Onyx SDK, hit-testing UI elements, `BitmapManager` rendering |

**`ViewportManager`** owns the `transformMatrix` (Note → Surface) and `inverseMatrix` (Surface → Note).

```
SurfaceCoords = (NoteCoords - scroll) * scale
NoteCoords    = SurfaceCoords / scale + scroll
```

Key methods:
- `viewportManager.surfaceToNoteCoordinates(x, y)` — use when receiving stylus input
- `viewportManager.noteToSurfaceCoordinates(x, y)` — use when checking visibility or drawing to bitmap

**Rule:** Shapes are ALWAYS stored and managed in NoteCoordinates. They are converted to SurfaceCoordinates only at render time inside `BitmapManager.recreateBitmapFromShapes()`.

---

## 3. Startup Sequence

```
MainActivity.onCreate()
  └─► OnyxDrawingActivity.onCreate()
        └─► BaseDrawingActivity.onCreate()
              ├─ runBlocking { noteRepository.setCurrentNote(noteId) }  // must finish before UI
              ├─ EditorViewModel created
              └─ setEditorViewAsContent()
                   └─ Compose: EditorView → DrawingCanvas (SurfaceView)
                        └─ ViewTreeObserver.OnGlobalLayoutListener fires when SurfaceView has size
                             └─ onSurfaceViewCreated(sv, vm)  [callback chain up to BaseDrawingActivity]
                                  └─ handleSurfaceViewCreated(sv, vm)
                                       ├─ initializeBitmapManager()     [OnyxDrawingActivity]
                                       ├─ initializeGestureHandler()    [BaseDrawingActivity]
                                       ├─ setViewModel(vm)              [sets screenWidth]
                                       ├─ initializeShapeMaanager()     [loads shapes from current note]
                                       ├─ initializeStylusHandler()     [wires RawInputCallback]
                                       ├─ editorViewModel.setDrawingReferences(...)
                                       ├─ initializePaint()
                                       ├─ initializeDeviceReceiver()
                                       ├─ initializeSurfaceCallback()   [SurfaceHolder.Callback]
                                       ├─ createTouchHelper()           [Onyx TouchHelper]
                                       └─ setObservers()                [StateFlow watchers]
```

**`SurfaceHolder.Callback.surfaceCreated()`** is the first place a bitmap can be safely drawn:
```
surfaceCreated → cleanSurfaceView() → createDrawingBitmap() → renderToScreen()
```

After `handleSurfaceViewCreated`, the app is fully wired and ready to draw.

---

## 4. Drawing — Stroke Lifecycle

This is the critical path for every new stroke.

```
[Onyx SDK] pen-down event
  └─► RawInputCallback.onBeginRawDrawing()
        └─► OnyxStylusHandler → AbstractStylusHandler.beginDrawing()
              ├─ isDrawingInProgress = true
              ├─ onDrawingStateChanged(true) → disableFingerTouch()
              └─ viewModel.startDrawing()

[Onyx SDK] pen-move events — SDK renders ink directly to e-ink display (raw drawing mode)
  (no app code involved during the stroke itself for normal pen types)

[Onyx SDK] pen-up → touchPointList delivered
  └─► RawInputCallback.onRawDrawingTouchPointListReceived(touchPointList)
        ├─ handleCancelledStroke()  // early exit if selection was cancelled
        ├─ handleSelectorStrokeEnd() // early exit if selector tool active
        └─ finalizeStroke(touchPointList)
              ├─ convertTouchPointListToNoteCoordinates()  // Surface → Note coords
              ├─ DrawManager.newShape(noteCoordList)
              │     ├─ createShapeFromPenType()             // create BaseShape
              │     ├─ onShapeCompleted(id, points, ...)   // notify ViewModel
              │     │     └─ EditorViewModel.addShape()
              │     │           ├─ noteRepository.addShape() // persist to DB
              │     │           ├─ ActionManager.recordAction(DrawAction)  // undo stack
              │     │           └─ htrRunManager.addShapesForRecognition() // HTR
              │     ├─ bitmapManager.renderShapeToBitmap(shape)  // draw to offscreen bitmap
              │     └─ bitmapManager.renderBitmapToScreen()      // push bitmap to SurfaceView
              ├─ shapesManager.addShape(newShape)  // add to in-memory list
              ├─ isDrawingInProgress = false
              ├─ onDrawingStateChanged(false) → enableFingerTouch()
              └─ viewModel.endDrawing()
```

**Important:** `renderShapeToBitmap()` draws the shape in **SurfaceCoordinates** (the shape passed in is temporarily given surface-space points). The shape's stored `touchPointList` remains in NoteCoordinates.

**Two bitmaps are NOT used.** There is one offscreen `Bitmap` (`BaseDrawingActivity.bitmap`) backed by a `Canvas` (`bitmapCanvas`). The Onyx SDK's raw drawing renders its own ink overlay directly on the e-ink display during the stroke; once the stroke ends, `renderBitmapToScreen()` composites the final result.

---

## 5. Canvas Update & Screen Refresh

### The Bitmap

`BaseDrawingActivity` owns:
- `bitmap: Bitmap` — the offscreen image (same size as SurfaceView)
- `bitmapCanvas: Canvas` — backed by the bitmap

`BitmapManager` is given lambdas to access these: `getBitmap()` and `getBitmapCanvas()`.

### Two refresh paths

**1. Incremental (after a single new stroke):**
```
DrawManager.newShape()
  ├─ bitmapManager.renderShapeToBitmap(shape)  // append shape to existing bitmap
  └─ bitmapManager.renderBitmapToScreen()       // push bitmap to SurfaceView
```

**2. Full recreate (after viewport change, erase, undo, note switch, etc.):**
```
OnyxDrawingActivity.forceScreenRefresh()
  ├─ RenderingUtils.enableScreenPost(surfaceView)  // tells Onyx EPD to accept new frame
  ├─ recreateBitmapFromShapes()
  │     └─ BitmapManager.recreateBitmapFromShapes(shapesManager.shapes())
  │           ├─ canvas.drawColor(WHITE)           // clear bitmap
  │           ├─ templateRenderer.drawTemplate()   // ruled lines, grid, etc.
  │           └─ for each shape:
  │                 ├─ isShapeVisible()            // cull off-screen shapes
  │                 ├─ notePointsToSurfaceTouchPoints()  // Note → Surface coords
  │                 └─ shape.render(renderContext) // draw to bitmapCanvas
  └─ renderToScreen(surfaceView, bitmap)
        └─ rxManager.enqueue(PaginationRendererToScreenRequest)
              └─ PaginationRendererToScreenRequest.execute()
                    ├─ EpdController.setViewDefaultUpdateMode(HAND_WRITING_REPAINT_MODE)
                    ├─ holder.lockCanvas()
                    ├─ RenderingUtils.renderBackground(canvas, viewRect)  // white bg
                    ├─ RenderingUtils.drawRendererContent(bitmap, canvas) // blit bitmap
                    ├─ DrawingManager.drawPageSeparators()  // if pagination enabled
                    ├─ holder.unlockCanvasAndPost(canvas)
                    └─ EpdController.resetViewUpdateMode()
```

### Why RxManager?

The Onyx SDK requires screen operations to be serialized on a single background thread. `RxManager.enqueue()` queues requests onto that thread. **Never call `holder.lockCanvas()` from the main thread on Onyx hardware** — it causes display artifacts.

### `cleanSurfaceView()` vs `forceScreenRefresh()`

- `cleanSurfaceView()` — locks the SurfaceView canvas directly and fills it white. Used only on first surface creation. **No bitmap involved.**
- `forceScreenRefresh()` — recreates the bitmap from all shapes, then renders it via the EPD request. Used for all subsequent updates.

---

## 6. Erasing

```
[Onyx SDK] eraser pen-down
  └─► onBeginRawErasing() → AbstractStylusHandler.beginErasing()
        ├─ isErasingInProgress = true
        └─ viewModel.startErasing()

[Onyx SDK] eraser pen-up → erase point list delivered
  └─► onRawErasingTouchPointListReceived(touchPointList)
        ├─ convertTouchPointListToNoteCoordinates()
        └─ finalizeErase(noteErasePointList)
              └─ EraseManager.handleErasing(noteErasePointList, shapesManager)
                    ├─ findIntersectingShapes()      // hit-test each shape with ERASE_RADIUS=15px
                    ├─ calculateRefreshRect()        // bounding union of erased shapes
                    ├─ shapesManager.removeAll(intersectingShapes)
                    ├─ for each shape: onShapeRemoved(shape.id)
                    │     └─ EditorViewModel.removeShape()
                    │           ├─ pendingErasedShapes.add(shape)  // for undo grouping
                    │           └─ noteRepository.removeShape()
                    ├─ PartialEraseRefresh.performPartialRefresh()  // Onyx only
                    │     └─ RxManager.enqueue(PartialRefreshRequest)
                    │           // renders only affected region to a temp bitmap
                    └─ bitmapManager.recreateBitmapFromShapes()  // full bitmap rebuild

[Onyx SDK] eraser pen-up event
  └─► onEndRawErasing() → AbstractStylusHandler.endErasing()
        ├─ isErasingInProgress = false
        ├─ viewModel.endErasing()
        │     └─ ActionManager.recordAction(EraseAction)  // undo all erased shapes as one action
        └─ onForceScreenRefresh()  // final screen update
```

**Note:** Erasing always triggers a full `recreateBitmapFromShapes()` because a partial redraw of the remaining shapes after removing some is complex. The partial EPD refresh (`PartialEraseRefresh`) only affects the e-ink hardware display overlay during the erase gesture itself; the main bitmap is always fully rebuilt.

**Hit-testing** is done in NoteCoordinates. The erase touch points are converted from Surface → Note before being passed to `EraseManager`.

---

## 7. Viewport — Pan & Zoom

`ViewportManager` (owned by `EditorViewModel`) holds:
- `scale: Float` — current zoom level (0.5–2.0)
- `scrollX: Float` — horizontal scroll in NoteCoordinates
- `scrollY: Float` — vertical scroll in NoteCoordinates

Both pan and zoom update the `ViewportState` StateFlow. The observer in `BaseDrawingActivity.setObservers()` reacts to every change:

```kotlin
editorViewModel.viewportState.collect { _ ->
    onViewportChanged()  // → OnyxDrawingActivity.forceScreenRefresh()
}
```

**Pan (finger drag):**
```
GestureHandler.handleMove() → viewportManager.updateOffset(deltaX, deltaY)
  // deltaX > 0 means finger moved right → scrollX decreases (content moves right)
```

**Zoom (pinch):**
```
ScaleGestureDetector.onScale() → viewportManager.updateScale(scaleFactor, focusX, focusY)
  // Focus point stays fixed: newScrollX = notePoint.x - focusX / newScale
```

**ScrollX constraint:**
- If pagination enabled and scale < 1.0: horizontally centers the page.
- Otherwise: clamped to >= 0 (can't scroll left of the note origin).

**Persistence:** Viewport state is auto-saved to DB with a 100ms debounce whenever it changes. On note open, state is restored via `viewportManager.setState()`.

---

## 8. Enabling and Disabling Drawing

The Onyx SDK's `TouchHelper` controls whether stylus input is captured. Drawing must be disabled when:
- The stroke options panel is open (stylus should not draw through it)
- The app is paused

**Flow:**
```
EditorViewModel._uiState.isStrokeOptionsOpen changes
  └─► setObservers() observer: editorViewModel.uiState.collect { state ->
        setDrawingEnabled(!state.isStrokeOptionsOpen)
  └─► OnyxDrawingActivity.setDrawingEnabled(enabled)
        if (!enabled): onyxTouchHelper.setRawDrawingEnabled(false)
        if (enabled + pen tool): reconfigureTouchHelper(...)
        if (enabled + selector tool): reconfigureTouchHelperForSelection()
```

**`reconfigureTouchHelper()`** sequence:
1. `setRawDrawingEnabled(false)` — stops capturing input
2. `closeRawDrawing()` — releases Onyx SDK resources
3. Configure: `setStrokeWidth`, `setStrokeColor`, `setLimitRect` (active area), `setLimitRect` exclusion rects
4. `openRawDrawing()` — re-initializes Onyx SDK
5. `setRawDrawingEnabled(true)` and `setRawDrawingRenderEnabled(true)`

This full reconfigure cycle is required whenever pen profile, exclusion zones, or drawing mode changes.

**Exclusion zones** are screen `Rect`s where the Onyx SDK will NOT capture stylus input (the toolbar region, stroke options panel, page separator bands). They are maintained in `EditorViewModel.excludeRects` and updated by:
- `Toolbar` when the stroke options panel opens/closes
- `BaseDrawingActivity.updatePaginationExclusionZones()` when pagination state or viewport changes

---

## 9. Gestures (Finger Touch)

Finger touch events flow differently than stylus events.

```
SurfaceView.setOnTouchListener { event ->
    if (isStrokeOptionsOpen) {
        if (ACTION_DOWN) editorViewModel.closeStrokeOptions()
        return true  // consume all events while panel open
    }
    if (hasStylusOrEraser || isErasing) return false  // let Onyx SDK handle
    gestureHandler.onTouchEvent(event)  // finger gestures
}
```

`GestureHandler` detects:
- **Pan** (1 finger drag) → `viewportManager.updateOffset()` → viewport state change → forceScreenRefresh
- **Pinch** (2 finger) → `viewportManager.updateScale()` → same chain
- **Flick** (fast swipe) → configurable action via `GestureMapping`
- **Tap** (1/2/3/4 fingers, single/double) → configurable action

Configurable actions include: `SCROLL`, `ZOOM`, `RESET_ZOOM_AND_CENTER`, `TOGGLE_SELECTION_MODE`, `NONE`.

Gesture mappings are loaded from `GestureSettingsRepository` and set on `GestureHandler.gestureMappings` during `initializeGestureHandler()`.

---

## 10. Selection, Move, Scale, Rotate

When `Tool.SELECTOR` is active, stylus strokes become selection/transform gestures instead of ink strokes.

### Lasso selection

```
onBeginRawDrawing() + tool == SELECTOR
  └─► AbstractStylusHandler.beginSelectionStroke()
        └─► handleSelectionBegin()
              if (no existing selection): selectionManager.beginLasso()

onRawDrawingTouchPointListReceived()
  └─► handleSelectorStrokeEnd()
        └─► handleSelectionInput()
              selectionManager.addLassoPoints(notePointList)
              selectionManager.finishLasso(shapes)  // hit-tests lasso polygon against shapes
              if (hasSelection): onLassoSelectionCompleted()
                  → onyxTouchHelper.setRawDrawingRenderEnabled(false)  // hide lasso ink
```

### Transform (with existing selection)

```
onBeginRawDrawing() + tool == SELECTOR + hasSelection
  └─► handleSelectionBegin()
        ├─ isOnRotationHandle() → beginRotate()
        ├─ isOnScaleHandle()   → beginScale()
        ├─ isInsideBoundingBox() → beginDrag()
        └─ outside → cancelSelection() → selectionCancelledThisStroke = true
```

During move: `onRawDrawingTouchPointMoveReceived()` fires `handleSelectionMoveUpdate()` every 100 events (`REFRESH_COUNT_LIMIT`), which calls `selectionManager.updateDrag/updateScale/updateRotate()` then `forceScreenRefresh()`.

On pen-up: `handleSelectionInput()` calls `finishDrag/finishScale/finishRotate()`, which returns the delta/factor/angle, then:
1. `viewModel.recordMoveAction/recordTransformAction()` — adds to undo stack
2. `viewModel.persistMovedShapes/persistScaledShapes/persistRotatedShapes()` — updates DB

**`forceScreenRefresh()` during selection** draws the selection overlay on top of the bitmap:
```
recreateBitmapFromShapes()
  └─ (in BaseDrawingActivity.recreateBitmapFromShapes())
       ├─ bitmapManager.recreateBitmapFromShapes(shapesManager.shapes())
       └─ if (selectionManager.hasSelection):
            bitmapManager.drawSelectionOverlay(selectionManager, viewportManager)
              ├─ SelectionRenderer.drawLasso()     // lasso outline
              ├─ SelectionRenderer.drawBoundingBox()
              └─ SelectionRenderer.drawHandles()   // scale corners + rotate handle
```

---

## 11. Undo / Redo

`ActionManager` maintains two stacks. Each action implements `ActionInterface`:
```kotlin
interface ActionInterface {
    suspend fun undo()
    suspend fun redo()
}
```

**Action types:**

| Class | Created when | undo() | redo() |
|-------|-------------|--------|--------|
| `DrawAction` | stroke completed | remove shape from DB + ShapesManager | re-add shape |
| `EraseAction` | erase gesture ends | re-add all erased shapes | remove them again |
| `MoveAction` | drag selection ends | move shapes back by -dx, -dy | move forward again |
| `TransformAction` | scale/rotate ends | invert transform | re-apply transform |

After undo/redo, `bitmapManager.recreateBitmapFromShapes()` is called but `renderBitmapToScreen()` is NOT — the caller (`EditorViewModel.undo/redo`) calls `onScreenRefreshNeeded()` which maps to `forceScreenRefresh()`.

**Erase grouping:** Erasing multiple shapes in one pen stroke is recorded as a single `EraseAction`. `EditorViewModel` accumulates `pendingErasedShapes` while `isErasingInProgress == true`, then creates the action in `endErasing()`.

---

## 12. Pagination & Paper Templates

**Pagination** divides the infinite canvas into fixed-height pages. Page height = `screenWidth * paperSize.aspectRatio`.

When enabled:
- Page separators (blue bands) are drawn in `PaginationRendererToScreenRequest.execute()` by calling `DrawingManager.drawPageSeparators()` — directly onto the SurfaceView canvas (NOT the bitmap). This means they always appear on top.
- Exclusion zones for the separator bands are added to `TouchHelper` to prevent stylus drawing inside them.
- Horizontal scroll is constrained to center the page.

**Paper templates** (`PaperTemplate`: BLANK, RULED, GRID, DOTTED) are drawn onto the bitmap by `PaperTemplateRenderer` before shapes are drawn, in `BitmapManager.recreateBitmapFromShapes()`.

**State flow for pagination:**
```
NoteSettingsDialog → viewModel.updatePaginationEnabled(true)
  ├─ _isPaginationEnabled.value = true
  ├─ viewportManager.isPaginationEnabled = true
  └─ noteRepository.updatePaginationSettings()  // persist

Observer in BaseDrawingActivity:
  isPaginationEnabled.collect → updatePaginationExclusionZones()
  viewportState.collect → updateCurrentPage(), updatePaginationExclusionZones()
```

---

## 13. Data Layer — Persistence

```
Room Database (NotesDatabase)
├─ NoteEntity  ←→  NoteDao
├─ ShapeEntity ←→  ShapeDao
├─ NotebookEntity ←→ NotebookDao
├─ FolderEntity ←→ FolderDao
├─ RecognizedSegmentEntity ←→ RecognizedSegmentDao
├─ DeletedItemEntity ←→ DeletedItemDao  (for sync tombstones)
└─ SyncStateEntity ←→ SyncStateDao
```

**NoteRepository** is the single access point for note + shape operations. Key behavior:
- `setCurrentNote(noteId)` loads the note + all its shapes into `_currentNote: MutableStateFlow<Note>`.
- After any mutation (addShape, removeShape, updateShape), `refreshCurrentNoteIfMatch()` reloads the in-memory note so `currentNote.value` stays current.
- `ShapesManager` is initialized from `editorViewModel.currentNote.value.shapes` — this is the snapshot at init time.

**Domain models vs entities:**
- `Shape` (domain) — `PointF` list, pressure list, pen type enum, timestamps
- `ShapeEntity` (Room) — same data with `noteId` foreign key; points serialized via `Converters`
- `BaseShape` (Onyx SDK) — `TouchPointList`-based, renderable by the SDK

**ShapesManager and NoteRepository can drift** if `ShapesManager` is not rebuilt after note mutations from outside the current session (e.g., undo). Both are always updated together in `ActionUtils.addShapeToNoteAndMemory/removeShapeFromNoteAndMemory`.

---

## 14. UI Layer (Compose)

```
EditorView (Composable)
├─ Toolbar
│   ├─ 5 × ProfileButton (pen type + color dot)
│   ├─ Selector tool button
│   ├─ Undo / Redo buttons
│   ├─ Settings button → NoteSettingsDialog
│   ├─ Navigation buttons (prev/next note in notebook)
│   ├─ Collapse button
│   └─ StrokeOptionsPanel (Popup, rendered above SurfaceView)
├─ DrawingCanvas
│   └─ SurfaceView (wrapped in AndroidView)
└─ ViewportInfo overlay (zoom %, current page)
```

**StrokeOptionsPanel** is rendered in a `Popup` so it floats above the SurfaceView. Its screen `Rect` is measured via `onGloballyPositioned` and added to `viewModel.excludeRects` as an exclusion zone. When it closes, the rect is removed.

**Why is drawing on SurfaceView not pure Compose?** The Onyx SDK's `TouchHelper` requires a `SurfaceView` specifically. It intercepts stylus events at a low level before Android's normal event dispatch. All Compose UI is layered on top.

**State flows consumed in Compose:**
- `uiState` — `isStrokeOptionsOpen`, `selectedTool`
- `currentPenProfile` — updates toolbar indicators
- `viewportState` — updates `ViewportInfo` overlay
- `isPaginationEnabled`, `paperSize`, `paperTemplate` — passed to dialogs
- `currentPageNumber` — displayed in ViewportInfo
- `canUndo`, `canRedo` — undo/redo button enabled state
- `canGoBack`, `canGoForward` — notebook navigation buttons

---

## 15. Observers in BaseDrawingActivity

`setObservers()` (called once after surface is ready) sets up these `lifecycleScope.launch` collectors:

| Flow | Action |
|------|--------|
| `currentPenProfile` | `updatePenProfile()` → updates paint, reconfigures TouchHelper |
| `isPaginationEnabled` | `updatePaginationExclusionZones()` |
| `viewportState` | `updateCurrentPage()`, `updatePaginationExclusionZones()`, `onViewportChanged()` → `forceScreenRefresh()` |
| `canUndo` | `refreshUIChrome()` — forces e-ink toolbar redraw |
| `canRedo` | `refreshUIChrome()` |
| `uiState` | `setDrawingEnabled(!isStrokeOptionsOpen)` |
| `refreshUi` | `forceScreenRefresh()` — explicit refresh (e.g., after dialog close) |

**`refreshUIChrome()`** is Onyx-specific: calls `RenderingUtils.enableScreenPost(window.decorView)` + `postInvalidate()`. On e-ink the Compose UI layer doesn't refresh automatically — this forces it.

---

## 16. Note Navigation in a Notebook

When a note is opened from a notebook, `EditorViewModel` has a non-null `notebookId`.

```
viewModel.navigateForward()
  ├─ get notes in notebook (ordered)
  ├─ if next note exists → switchToNote(nextNote.id)
  └─ if at end + current has shapes → createNoteInNotebook() → switchToNote(newNote.id)

switchToNote(noteId)
  ├─ noteRepository.setCurrentNote(noteId)   // loads new note's shapes into currentNote
  ├─ viewportManager.setState(...)           // restore saved viewport
  ├─ _isPaginationEnabled.value = ...        // restore note settings
  ├─ actionManager.clear()                   // undo history is per-note
  ├─ updateNavigationState()
  └─ onNoteSwitched?.invoke()
       └─ BaseDrawingActivity.onNoteSwitched()
             ├─ bitmapCanvas.drawColor(WHITE)    // clear bitmap
             ├─ initializeShapeMaanager()         // rebuild ShapesManager from new note
             ├─ editorViewModel.setDrawingReferences(...)
             └─ recreateBitmapFromShapes() + renderToScreen()
```

---

## 17. SDK Abstraction Layer

The app is designed to support multiple device SDKs via the template method pattern.

```
BaseDrawingActivity (abstract)
├─ abstract: initializeShapeMaanager(), initializeStylusHandler(), createDeviceReceiver()
├─ abstract: enableFingerTouch(), disableFingerTouch()
├─ abstract: cleanSurfaceView(), renderToScreen()
├─ abstract: onResumeDrawing(), onPauseDrawing(), onCleanupSDK()
├─ abstract: updateActiveSurface(), updateTouchHelperWithProfile()
├─ abstract: updateTouchHelperExclusionZones(), initializeDeviceReceiver()
├─ abstract: onCleanupDeviceReceiver(), onViewportChanged()
└─ concrete: onCreate(), handleSurfaceViewCreated(), setObservers(), etc.

OnyxDrawingActivity extends BaseDrawingActivity
└─ Uses: TouchHelper, RxManager, EpdController, GlobalDeviceReceiver

GenericDrawingActivity extends BaseDrawingActivity
└─ Uses: standard MotionEvents, direct canvas operations

AbstractStylusHandler (shared drawing/erasing/selection logic)
├─ OnyxStylusHandler — wires Onyx RawInputCallback
└─ GenericStylusHandler — wires Android MotionEvents
```

To add a new device SDK:
1. Create `NewSdkDrawingActivity : BaseDrawingActivity()`
2. Create `NewSdkStylusHandler : AbstractStylusHandler()`
3. Implement all abstract methods

---

## 18. Known Pain Points & Refactor Hints

### A. ShapesManager / NoteRepository dual state
**Problem:** Shapes exist in two places: `ShapesManager` (in-memory `BaseShape` list) and `NoteRepository` (DB `Shape` list in `currentNote.value`). These can drift. `ActionUtils` carefully updates both, but direct calls to only one are a bug waiting to happen.

**Refactor hint:** Consider making `ShapesManager` a reactive wrapper around the DB via a Flow, so it can never drift. Or document a strict rule: always use `ActionUtils` methods, never mutate one without the other.

### B. `recreateBitmapFromShapes()` is called too often
**Problem:** Full bitmap reconstruction is O(n shapes) and happens on every viewport change, every erase, every undo, and any `forceScreenRefresh()`. At scale, this becomes slow.

**Refactor hint:** Explore caching the bitmap at a given viewport and invalidating only when shapes change (not when viewport changes). Viewport-only changes could just re-blit with a matrix transform.

### C. Coordinate conversion inside shape rendering
**Problem:** `BitmapManager.recreateBitmapFromShapes()` temporarily swaps the shape's `touchPointList` with a surface-coordinate copy for rendering, then restores it. This mutation of a shared object is fragile — if an exception occurs between the swap and restore, the shape's data is corrupted.

**Refactor hint:** Refactor `BaseShape.render()` to accept a coordinate-space parameter, or pass the surface points as a separate argument without touching the shape object.

### D. `reconfigureTouchHelper()` is expensive
**Problem:** Every pen profile change, exclusion zone change, or tool switch calls `closeRawDrawing()` + `openRawDrawing()`. This causes a visible e-ink flash and introduces latency.

**Refactor hint:** Batch changes where possible. Debounce exclusion zone updates. Only call `openRawDrawing()` when truly necessary (e.g., when stroke width or active area changes).

### E. `BaseDrawingActivity` is getting large
The class handles: surface lifecycle, bitmap lifecycle, paint, observer setup, pen profile, pagination zones, gesture handler init, note switching, undo/redo wiring, device receiver. It is approaching the point where it should be split into focused coordinators.

**Refactor hint:** Extract `DrawingSurfaceCoordinator` (bitmap + surface lifecycle), `ObserverCoordinator` (StateFlow observers), and `PenProfileCoordinator` (profile updates + TouchHelper sync).

### F. Stroke options panel state is split
Panel open/close state lives in `EditorViewModel.uiState.isStrokeOptionsOpen`, but the exclusion rect for the panel lives in `Toolbar`'s local `strokePanelRect` state. Closing the panel from canvas touch (in `createTouchHelper()`) calls `viewModel.closeStrokeOptions()` directly, bypassing Toolbar's `closeStrokeOptionsPanel()` which also removes the rect.

**Refactor hint:** Make the ViewModel the single source of truth for panel state AND the exclusion rect. Toolbar should only read state, not own it.

### G. `ShapesManager.convertDomainShapeToSdkShape()` and `DrawManager.createShapeFromPenType()` duplicate shape creation logic
Both build a `BaseShape` from pen type, color, stroke width. The code is nearly identical.

**Refactor hint:** Merge into a single factory method, probably in `ShapeFactory` or a new `ShapeBuilder` util.
