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
        val imagesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        val storageGranted = hasLegacyStorage(context)
        BumpDeskLog.d(
            BumpDeskLog.Tag.WALLPAPER,
            tag,
            "READ_MEDIA_IMAGES=$imagesGranted READ_EXTERNAL_STORAGE=$storageGranted " +
                "appOp=${if (hasAppOpAccess(context)) "allowed" else "blocked"} " +
                "canReadFile=${canReadWallpaperFile(context)}"
        )
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
