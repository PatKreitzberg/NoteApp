# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Only `gradlew.bat` exists (no Unix `gradlew`). Use the Gradle wrapper or Android Studio:

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Unit tests (JVM)
./gradlew connectedAndroidTest   # Instrumented tests (requires device/emulator)
```

No custom lint config. No CI/CD pipeline.

## Project Overview

Android drawing/note-taking app targeting **Onyx e-ink devices**. Kotlin + Jetpack Compose + Onyx SDK.

- **Namespace:** `com.wyldsoft.notes`
- **Min SDK:** 29 | **Target/Compile SDK:** 35
- **Kotlin:** 2.1.10 | **Compose:** 1.7.8 | **JVM target:** 17
- **Orientation:** Portrait only

## Architecture

Source root: `app/src/main/java/com/wyldsoft/notes/`

### Activity hierarchy (Template Method pattern)
```
ComponentActivity
  └─ BaseDrawingActivity (abstract — bitmap, paint, SurfaceView lifecycle)
       └─ OnyxDrawingActivity (Onyx TouchHelper, RawInputCallback, RxManager)
            └─ MainActivity (app entry point)
```

### Key packages
| Package | Purpose |
|---------|---------|
| `sdkintegration/` | SDK abstraction layer; `onyx/` subpackage for Onyx-specific code |
| `shapemanagement/` | `ShapeFactory`, `EraseManager`; `shapes/` has `Shape.java` base + pen-type subclasses |
| `rendering/` | `RenderContext`, `RendererToScreenRequest`, `RenderingUtils` |
| `pen/` | `PenProfile` (stroke width/color/type), `PenTypes` enum (8 types) |
| `editor/` | `EditorState` (SharedFlow-based global state), `EditorView` (Compose) |
| `refreshingscreen/` | `PartialEraseRefresh` for e-ink optimization |

### Drawing data flow
1. Onyx SDK delivers `TouchPointList` via `RawInputCallback` on pen-up
2. `OnyxDrawingActivity.drawScribbleToBitmap()` → `ShapeFactory` creates typed `Shape`
3. Shape renders to bitmap via `shape.render(RenderContext)`
4. Bitmap drawn to `SurfaceView` via `RendererToScreenRequest` (serialized through `RxManager`)

### Erasing flow
`EraseManager.findIntersectingShapes()` hit-tests erase points against shapes → removes matches → recreates bitmap.

### Shape type mapping
PenType → ShapeFactory constant → Shape subclass:
- BALLPEN/PENCIL → `NormalPencilShape` (quad Bézier paths)
- FOUNTAIN → `BrushScribbleShape` (NeoFountainPen)
- MARKER → `MarkerScribbleShape` (NeoMarkerPen)
- CHARCOAL/CHARCOAL_V2 → `CharcoalScribbleShape` (NeoCharcoalPenV2)
- NEO_BRUSH → `NewBrushScribbleShape` (NeoBrushPen)

### State management
`EditorState` companion object exposes `MutableSharedFlow` channels: `refreshUi`, `drawingStarted`, `drawingEnded`, `forceScreenRefresh`, `isStrokeOptionsOpen`.

### Not yet implemented
Coordinate transforms (ViewportManager), pan/zoom, selection tool, undo/redo (ActionManager), pagination, Room DB persistence, notebook navigation. See `ARCHITECTURE.md` for planned designs.

## Conventions

- Keep classes under 300 lines
- Commit before and after major changes
- Add new files to git and do a git commit after each code change
- Write new classes/files in Kotlin, not java
- Onyx SDK repo uses insecure HTTP (`repo.boox.com`) — this is intentional
- Whenever significant code changes are implement check it compiles
- When creating new classes make a TAG = <class name> and add Log.d(TAG, "<function name>") for each function larger than four lines.

## Key Dependencies

- **Onyx SDK:** `onyxsdk-pen` 1.4.12, `onyxsdk-device` 1.2.32 (e-ink drawing, TouchHelper, EpdController)
- **RxJava2:** Used by `RxManager` for serialized screen rendering operations
- **Room:** 2.6.1 (KSP compiler) — schema files in `app/schemas/`
- **ML Kit Digital Ink:** 18.1.0 (handwriting recognition)
- **jnanoid:** ID generation for shapes

## Reference

- `ARCHITECTURE.md` — 644-line deep reference covering all subsystems and planned features
