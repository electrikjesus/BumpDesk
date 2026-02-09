# BumpDesk

BumpDesk is a 3D Android launcher inspired by the BumpTop desktop metaphor. It provides an immersive workspace where apps, widgets, and files are treated as physical objects in a 3D room.

## Features

- **3D Room Metaphor**: Use the floor and walls to organize your digital life.
- **Radial Context Menus**: Touch-first context menus that appear around your point of interaction.
- **Piles and Folders**: Stack icons into piles or expand them into organized 3D grids.
- **Recent Apps Carousel**: A dedicated widget on the back wall to quickly navigate through recently used apps using snapshots.
- **Advanced Launch Options**: Open apps in Fullscreen, Freeform, or Pinned modes directly from the context menu.

## System Features & ADB Commands

Some features require system-level permissions or settings to be enabled. Use the following ADB commands to grant necessary access for testing:

### 1. Enable Recent Apps Carousel (Usage Stats)
BumpDesk needs to see which apps you've used recently to fill the carousel.
```bash
adb shell appops set com.bass.bumpdesk GET_USAGE_STATS allow
```

### 2. Enable Freeform Window Support
To test the "Open As > Freeform" feature, freeform windows must be enabled at the system level.
```bash
adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
# A reboot is usually required for these changes to take effect.
adb reboot
```

### 3. Grant Signature Permissions (AOSP/Root Required)
For actual recents snapshots (instead of placeholders), the app needs `REAL_GET_TASKS`. If you are testing on a rooted device or custom AOSP build where you can grant signature permissions:
```bash
adb shell pm grant com.bass.bumpdesk android.permission.REAL_GET_TASKS
```

### 4. Set as Default Launcher
```bash
adb shell cmd package set-home-activity com.bass.bumpdesk/.LauncherActivity
```

## Development

### Prerequisites
- Android Studio Hedgehog or newer.
- Android device running API 29 (Android 10) or higher.
- Developer Options enabled.

### Build
Run the following command to build the debug APK:
```bash
./gradlew :app:assembleDebug
```
