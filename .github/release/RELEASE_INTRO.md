BumpDesk is an experimental **3D Android launcher** inspired by BumpTop. Apps, widgets, and notes behave like physical objects on a desk — you can move them, stack them into piles, pin them to walls, and interact with them in a 3D room (or flat-floor / infinite desktop modes).

**Advanced beta** — actively developed; desktop layouts persist across reboots via Room.

### Highlights

- Physics-based floor and wall workspace
- Smart piles, folders, and All Apps drawer
- 3D widgets and Recents with task previews (where supported)
- Material 3 settings, themes, and procedural shaders
- Radial menus with launch modes (fullscreen, freeform, pinned)

## Install

1. Download **`app-release.apk`** below (signed; recommended for everyday use).
2. Install on a device running **Android 12L+ (API 32+)** with OpenGL ES 2.0.
3. Set BumpDesk as your home app when prompted, or launch from the app drawer.
4. Complete the onboarding wizard.

```bash
adb install -r app-release.apk
```

### Recommended setup (ADB)

Enable usage stats (required for Recents):

```bash
adb shell appops set com.bass.bumpdesk GET_USAGE_STATS allow
```

**Wallpaper floor** (Flat floor mode, Android 13+ / Waydroid):

1. Toggle **Use system wallpaper as floor** in Settings and allow **Photos and videos** when prompted (or grant via adb).
2. For the **live** system wallpaper (not Pick Image), also grant legacy Storage — sideloaded apps cannot enable this from App Settings on Android 13–14:

```bash
adb shell pm grant com.bass.bumpdesk android.permission.READ_MEDIA_IMAGES
adb shell pm grant com.bass.bumpdesk android.permission.READ_EXTERNAL_STORAGE
```

Optional — freeform window launches:

```bash
adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
```

Optional — live task snapshots in Recents (root/AOSP):

```bash
adb shell pm grant com.bass.bumpdesk android.permission.REAL_GET_TASKS
```

Debug wallpaper loading:

```bash
adb logcat -s "BumpDesk:Wallpaper"
```

### Recent changes (wallpaper floor)

- **Waydroid / API 33+:** Fixed crash when enabling wallpaper floor (`WallpaperManager.getDrawable(int)` missing on some images); safe fallbacks and clearer dialogs after Photos permission is granted.
- **Permissions:** Runtime **Photos and videos** request; legacy **Storage** documented for live wallpaper via adb; **Pick Image** works without Storage.
- **Diagnostics:** Permission state logged under `BumpDesk:Wallpaper` (`WallpaperPermissions.diagnose()`).

More detail: [README](https://github.com/electrikjesus/BumpDesk/blob/main/README.md) · [Architecture / logcat tags](https://github.com/electrikjesus/BumpDesk/blob/main/docs/architecture.md) · [Report issues](https://github.com/electrikjesus/BumpDesk/issues)
