# BumpDesk - 3D Android Workspace

BumpDesk is an experimental 3D launcher for Android that reimagines the traditional home screen as a physical workspace. Inspired by the classic "BumpTop" desktop metaphor, it treats applications, widgets, and notes as physical objects that can be moved, stacked, pinned to walls, and interacted with in a natural way.

## 🌟 Key Features

- **Physics-Based Workspace**: Objects collide, bounce, and have "weight". Move items naturally around the room.
- **Surface Organization**: Use the floor for active work and walls for pinning important apps or widgets.
- **Smart Piles**: Gather items into piles by "lassoing" them. Piles can be fanned out for quick scanning or expanded into an organized grid.
- **Immersive Widgets**: Android widgets are rendered as 3D panels. Interact with them directly in the 3D scene.
- **Recent Tasks Carousel**: A dedicated 3D view on the back wall showing live snapshots of your recently used applications.
- **Dynamic Themes**: Fully customizable workspace appearance, including wall textures, floor materials, and selection colors.
- **Contextual UI**: Intuitive radial menus provide quick access to advanced launch modes (Freeform, Pinned, Fullscreen) and item properties.

## 🚀 Getting Started

### Prerequisites
- Android device running Android 10 (API 29) or higher.
- Support for OpenGL ES 2.0.

### Installation & Permissions
For the best experience, several system-level settings should be configured via ADB:

1. **Enable Usage Statistics** (Required for Recent Apps):
   ```bash
   adb shell appops set com.bass.bumpdesk GET_USAGE_STATS allow
   ```

2. **Enable Freeform Windowing** (For "Open As > Freeform"):
   ```bash
   adb shell settings put global enable_freeform_support 1
   adb shell settings put global force_resizable_activities 1
   adb reboot
   ```

3. **Grant Snapshot Permissions** (Optional - Requires Root/AOSP Privileges):
   To see live app snapshots in the Recents carousel instead of icons:
   ```bash
   adb shell pm grant com.bass.bumpdesk android.permission.REAL_GET_TASKS
   ```

## 🛠 Architecture

BumpDesk is built using:
- **OpenGL ES 2.0**: For high-performance 3D rendering.
- **Custom Physics Engine**: Optimized for touch-based mobile interaction.
- **Kotlin & Coroutines**: For efficient state management and background processing.
- **Room Persistence**: To save your desktop layout across reboots.

## 📂 Project Structure

- `com.bass.bumpdesk`: Core launcher logic.
- `com.bass.bumpdesk.persistence`: Database and entity definitions for layout saving.
- `assets/BumpTop`: Themed resources including textures and `theme.json` configurations.

## 📜 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
