package com.bass.bumpdesk

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

object WallpaperPermissions {

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasRuntimePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasLegacyStorage(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * On API 33+, [WallpaperManager] still checks [READ_EXTERNAL_STORAGE] at the binder.
     * [READ_MEDIA_IMAGES] alone does not satisfy that check.
     */
    fun needsStorageForWallpaper(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            Build.VERSION.SDK_INT <= 34 &&
            !hasLegacyStorage(context)
    }

    fun canReadWallpaperFile(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return hasLegacyStorage(context)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasRuntimePermission(context)
        } else {
            true
        }
    }

    /** On API 33+, [READ_EXTERNAL_STORAGE] cannot be requested at runtime when targeting SDK 33+. */
    fun canRequestLegacyStorageAtRuntime(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }

    /** True when live wallpaper still needs legacy storage before giving up. */
    fun shouldPromptLegacyStorage(context: Context): Boolean {
        return needsStorageForWallpaper(context)
    }

    data class Status(
        val apiLevel: Int,
        val readMediaImages: Boolean,
        val readExternalStorage: Boolean,
        val appOpAllowed: Boolean,
        val canReadWallpaperFile: Boolean,
        val needsLegacyStorage: Boolean,
        val canRequestLegacyAtRuntime: Boolean,
    ) {
        fun toLogString(): String =
            "API=$apiLevel READ_MEDIA_IMAGES=$readMediaImages READ_EXTERNAL_STORAGE=$readExternalStorage " +
                "appOp=${if (appOpAllowed) "allowed" else "blocked"} " +
                "canReadFile=$canReadWallpaperFile needsLegacyStorage=$needsLegacyStorage " +
                "canRequestLegacyAtRuntime=$canRequestLegacyAtRuntime"

        fun blockingReason(): String? = when {
            apiLevel >= Build.VERSION_CODES.TIRAMISU && !readMediaImages ->
                "Photos and videos access is not allowed. BumpDesk needs this permission " +
                    "before it can load a wallpaper for the floor."
            apiLevel >= Build.VERSION_CODES.TIRAMISU &&
                apiLevel <= 34 &&
                !readExternalStorage ->
                if (readMediaImages) {
                    "Photos access is granted, but Android 13–14 still requires legacy Storage " +
                        "to read the live system wallpaper. App Settings cannot grant that to sideloaded apps."
                } else {
                    "Live system wallpaper on Android 13–14 also needs legacy Storage, which " +
                        "Settings cannot grant to sideloaded apps. Pick an image, or grant Storage via adb."
                }
            !appOpAllowed ->
                "Storage access is blocked by App Ops. Open permission settings and allow access."
            else -> null
        }

        fun needsMediaImagesPrompt(): Boolean =
            apiLevel >= Build.VERSION_CODES.TIRAMISU && !readMediaImages
    }

    fun diagnose(context: Context): Status {
        val imagesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        return Status(
            apiLevel = Build.VERSION.SDK_INT,
            readMediaImages = imagesGranted,
            readExternalStorage = hasLegacyStorage(context),
            appOpAllowed = hasAppOpAccess(context),
            canReadWallpaperFile = canReadWallpaperFile(context),
            needsLegacyStorage = needsStorageForWallpaper(context),
            canRequestLegacyAtRuntime = canRequestLegacyStorageAtRuntime(context),
        )
    }

    fun hasAppOpAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val imagesMode = appOps.unsafeCheckOpNoThrow(
                "android:read_media_images",
                Process.myUid(),
                context.packageName
            )
            if (imagesMode == AppOpsManager.MODE_ALLOWED || imagesMode == AppOpsManager.MODE_DEFAULT) {
                return true
            }
        }
        val storageMode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_READ_EXTERNAL_STORAGE,
            Process.myUid(),
            context.packageName
        )
        return storageMode == AppOpsManager.MODE_ALLOWED || storageMode == AppOpsManager.MODE_DEFAULT
    }

    fun hasAccess(context: Context): Boolean {
        return hasRuntimePermission(context) && hasAppOpAccess(context)
    }

    fun permissionsGranted(results: Map<String, Boolean>): Boolean {
        return results.isNotEmpty() && results.values.all { it }
    }

    fun logStatus(context: Context, tag: String) {
        val status = diagnose(context)
        BumpDeskLog.d(BumpDeskLog.Tag.WALLPAPER, tag, status.toLogString())
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Opens the per-permission screen for Photos (Android 12+). Falls back to app details. */
    fun openPhotosPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent("android.intent.action.MANAGE_APP_PERMISSION").apply {
                    putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra("android.intent.extra.PERMISSION_NAME", Manifest.permission.READ_MEDIA_IMAGES)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Fall through to app details.
            }
        }
        openAppSettings(context)
    }
}
