# CLAUDE.md

# Humans rules:
1. Use MVVM architecture
2. After creating a new kotlin or java file add it to the git repo. Only add the new file.
3. Before implementing a major change, run 'git commit -am <message describing the changes that are about to be implemented>"
4. After implementing a major change, run 'git commit -am <message describing the changes that were just implemented and mention that it has not been tested>"
5. After writing new code check that no duplicate code was created.
6. If a class is larger than 300 lines, recomend refactoring it to the human user.

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
This is an Android note-taking application with advanced drawing capabilities, built specifically for Onyx e-ink devices. The app uses Jetpack Compose for UI and integrates with the Onyx SDK for stylus and drawing support.

## Build Commands

### Build the app
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug
```

### Run tests
```bash
# Run all unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run a specific test class
./gradlew test --tests "com.wyldsoft.notes.ExampleUnitTest"
```

### Code quality
```bash
# Run lint checks
./gradlew lint

# Check for dependency updates
./gradlew dependencyUpdates
```

### Development tasks
```bash
# Install debug APK on connected device
./gradlew installDebug

# Print version name
./gradlew printVersionName

# Generate database schemas
./gradlew generateSchemas
```

## Architecture Overview

### Core Architecture
The application follows a layered architecture with clear separation of concerns:

1. **Presentation Layer** (`presentation/`)
   - ViewModels: `EditorViewModel`, `HomeViewModel` - Handle UI state and business logic
   - Compose UI: `EditorView.kt`, `HomeView.kt` - Declarative UI components
   - State management: `EditorState.kt` - Centralized state for the drawing editor

2. **Domain Layer** (`domain/`)
   - Models: `Note.kt`, `Shape.kt` - Core business entities
   - Business logic interfaces

3. **Data Layer** (`data/`)
   - Database: Room database with entities, DAOs, and converters
   - Repository pattern: `NoteRepository`, `FolderRepository`, `NotebookRepository`

4. **SDK Integration Layer** (`sdkintegration/`)
   - Base abstractions: `BaseDrawingActivity`, `BaseDeviceReceiver`
   - Onyx-specific implementations: `OnyxDrawingActivity`, `OnyxStylusHandler`
   - Designed for multi-SDK support (future expansion to other devices)

### Key Components

**Drawing System**
- `DrawingManager.kt` - Coordinates drawing operations
- `rendering/` package - Different renderers for various drawing modes
- `shapemanagement/` - Shape creation and management
- `gestures/GestureHandler.kt` - Touch and gesture processing

**Screen Refresh Management**
- `refreshingscreen/` - Handles e-ink screen refresh strategies
- `ScreenRefreshManager.kt` - Coordinates partial and full refreshes
- `RefreshCoordinator.kt` - Manages refresh timing and regions

**Stylus Integration**
- `OnyxStylusHandler.kt` - Onyx SDK stylus event handling
- `pen/` - Pen profiles and types configuration
- Raw input processing for low-latency drawing

**UI Components**
- Compose-based UI with Material 3 design
- `Toolbar.kt` - Drawing tools and options
- `StrokeOptionsPanel.kt` - Stroke customization
- `ViewportInfo.kt` - Canvas viewport management

### Data Flow
1. Stylus/touch events → SDK layer → Drawing Manager
2. Drawing Manager → Shape creation → Renderer
3. Renderer → Canvas update → Screen refresh
4. UI actions → ViewModel → State update → Compose recomposition

### Important Patterns
- **Repository Pattern**: All data access goes through repositories
- **MVVM**: ViewModels manage UI state and coordinate with repositories
- **State Management**: Centralized state with SharedFlow/StateFlow
- **SDK Abstraction**: Base classes allow easy addition of new device SDKs

## Development Guidelines

### When modifying the drawing system:
1. Check `BaseDrawingActivity` for the drawing lifecycle
2. Review `EditorState` for state management
3. Test on actual Onyx hardware for stylus responsiveness

### When adding new features:
1. Follow the existing package structure
2. Use dependency injection via constructor parameters
3. Keep classes under 150 lines (excluding comments)
4. Add proper documentation for new components

### Database migrations:
- Schema versions are tracked in `NotesDatabase.kt`
- Always increment version and provide migration when changing entities
- Test migrations thoroughly before release

### Environment variables:
- `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` - For release signing
- `SHIPBOOK_APP_ID`, `SHIPBOOK_APP_KEY` - For crash reporting
- `IS_NEXT` - For development builds versioning