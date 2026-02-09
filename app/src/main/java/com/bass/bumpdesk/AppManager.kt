package com.bass.bumpdesk

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log

class AppManager(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    fun loadAllApps(): List<AppInfo> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(mainIntent, 0)
        return apps.map { resolveInfo ->
            AppInfo(
                label = resolveInfo.loadLabel(packageManager).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                icon = resolveInfo.loadIcon(packageManager)
            )
        }
    }

    fun getRecentApps(limit: Int = 12): List<AppInfo> {
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        val showRecents = prefs.getBoolean("show_recent_apps", true)
        if (!showRecents) return emptyList()

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val recentApps = mutableListOf<AppInfo>()

        try {
            // Task: Fetch actual task snapshots if possible (AOSP style)
            val tasks = am.getRecentTasks(limit, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
            tasks.forEach { task ->
                val resolveInfo = packageManager.resolveActivity(task.baseIntent, 0)
                if (resolveInfo != null && resolveInfo.activityInfo.packageName != context.packageName) {
                    val appInfo = AppInfo(
                        label = resolveInfo.loadLabel(packageManager).toString(),
                        packageName = resolveInfo.activityInfo.packageName,
                        icon = resolveInfo.loadIcon(packageManager)
                    )
                    
                    // Attempt to fetch snapshot via hidden API if available (reflection)
                    appInfo.snapshot = getTaskSnapshot(task.persistentId)
                    
                    recentApps.add(appInfo)
                }
            }
        } catch (e: SecurityException) {
            Log.e("AppManager", "Missing REAL_GET_TASKS permission for snapshots", e)
            // Fallback to UsageStats if permission denied
            return getRecentAppsViaUsageStats(limit)
        } catch (e: Exception) {
            Log.e("AppManager", "Error getting recent tasks", e)
        }

        return recentApps
    }

    private fun getRecentAppsViaUsageStats(limit: Int): List<AppInfo> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 60 * 24 // last 24h
        
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val recentApps = mutableListOf<AppInfo>()

        if (stats != null) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            for (usageStat in sortedStats) {
                if (recentApps.size >= limit) break
                if (usageStat.packageName == context.packageName) continue
                
                try {
                    val appInfo = packageManager.getApplicationInfo(usageStat.packageName, 0)
                    val label = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    recentApps.add(AppInfo(label, usageStat.packageName, icon))
                } catch (e: Exception) { }
            }
        }
        return recentApps
    }

    /**
     * Uses reflection to attempt to call hidden ActivityManager.getTaskSnapshot API.
     * This is intended for AOSP builds where the app is signed with the platform key.
     */
    private fun getTaskSnapshot(taskId: Int): Bitmap? {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            // In AOSP/Privileged environments, getTaskSnapshot exists
            val getTaskSnapshotMethod = am.javaClass.getMethod("getTaskSnapshot", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            val taskSnapshot = getTaskSnapshotMethod.invoke(am, taskId, false)
            
            if (taskSnapshot != null) {
                // TaskSnapshot has a getSnapshot() method that returns a HardwareBuffer or Bitmap depending on API level
                val getSnapshotMethod = taskSnapshot.javaClass.getMethod("getSnapshot")
                val buffer = getSnapshotMethod.invoke(taskSnapshot)
                
                // HardwareBuffer conversion to Bitmap (API 26+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && buffer is android.hardware.HardwareBuffer) {
                    return Bitmap.wrapHardwareBuffer(buffer, null)
                } else if (buffer is Bitmap) {
                    return buffer
                }
            }
        } catch (e: Exception) {
            // Expected to fail on non-AOSP/non-privileged builds
        }
        return null
    }
}
