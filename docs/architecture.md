# BumpDesk Architecture Summary

This document describes the high-level architecture of the BumpDesk project to help maintain context across development sessions.

## Overview
BumpDesk is a 3D physics-based launcher for Android, inspired by BumpTop. It uses OpenGL ES 2.0 for rendering and a custom physics engine for item interactions.

## Key Components

### 1. UI & Activities
*   **LauncherActivity**: The main entry point. Manages the `GLSurfaceView`, gesture detectors, and coordinates between the renderer and Android system UI (widgets, app launching).
*   **SettingsActivity**: Manages user preferences (themes, wallpaper, recents).
*   **RecentsActivity**: Handles the "Recents" intent to act as a system task switcher.

### 2. Rendering (Modularized)
Rendering logic is split from the main `BumpRenderer` to reduce file complexity:
*   **BumpRenderer**: The main `GLSurfaceView.Renderer`. Coordinates the overall frame, camera, and specific sub-renderers.
*   **ItemRenderer**: Draws individual `BumpItem` objects (Apps, Sticky Notes, Photos, Web Widgets).
*   **WidgetRenderer**: Specifically handles Android `AppWidgetHostView` textures and their projection into 3D.
*   **PileRenderer**: Handles the rendering logic for stacks/folders, including expanded views.
*   **UIRenderer**: Draws 3D overlays like folder close buttons, scroll arrows, and labels.
*   **RoomRenderer**: Draws the floor and walls using theme-specific textures.
*   **DefaultShader**: The primary GLSL shader program for lighting, texturing, and coloring.

### 3. State & Logic
*   **SceneState**: The single source of truth for all items on the desktop. Thread-safe using `CopyOnWriteArrayList`.
*   **InteractionManager**: Handles ray-casting for hit detection, dragging logic, lasso selection, and multi-touch gestures.
*   **PhysicsEngine / PhysicsThread**: Manages collisions, gravity, and "bump" effects in a separate thread.
*   **CameraManager**: Manages the 3D viewport, including smooth transitions between "Default", "Wall Focus", and "Folder Expanded" modes.

### 4. Support Managers
*   **ThemeManager**: Loads assets and colors from `assets/BumpTop/` theme folders.
*   **TextureManager**: Manages GL texture loading, caching, and updates.
*   **AppManager**: Interfaces with the Android `PackageManager` and `UsageStatsManager` for app lists and recents.
*   **DialogManager**: Centralizes all `AlertDialog` prompts for a cleaner `LauncherActivity`.
*   **ActionHandler**: Centralizes intent handling and app launching logic.

### 5. Persistence
*   **DeskDatabase (Room)**: Stores item positions, scales, and surface attachments.
*   **DeskRepository**: Provides a clean API for `BumpRenderer` to save/load the desk state.

## Data Flow
1.  **Input**: `LauncherActivity` receives `MotionEvent` -> passed to `InteractionManager`.
2.  **Interaction**: `InteractionManager` updates `BumpItem` positions or `SceneState` selections.
3.  **Physics**: `PhysicsThread` reads `SceneState`, applies forces, and updates velocities/positions.
4.  **Render**: `BumpRenderer` reads `SceneState` every frame -> calls sub-renderers to draw via `DefaultShader`.
5.  **Persistence**: `BumpRenderer` triggers `saveState` via `DeskRepository` on pause.
