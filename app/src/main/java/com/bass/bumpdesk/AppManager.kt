package com.bass.bumpdesk

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Process
import android.util.Log

class AppManager(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    interface RecentsUpdateListener {
        fun onRecentsUpdated(recents: List<AppInfo>)
    }

    private var updateListener: RecentsUpdateListener? = null

    fun setUpdateListener(listener: RecentsUpdateListener) {
        this.updateListener = listener
    }

    fun loadAllApps(): List<AppInfo> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(mainIntent, 0)
        return apps.map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val appInfo = AppInfo(
                label = resolveInfo.loadLabel(packageManager).toString(),
                packageName = packageName,
                icon = resolveInfo.loadIcon(packageManager)
            )
            appInfo.category = getAppCategory(packageName)
            appInfo
        }
    }

    private fun getAppCategory(packageName: String): AppInfo.Category {
        try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (info.category) {
                    ApplicationInfo.CATEGORY_GAME -> return AppInfo.Category.GAME
                    ApplicationInfo.CATEGORY_SOCIAL -> return AppInfo.Category.SOCIAL
                    ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppInfo.Category.PRODUCTIVITY
                    ApplicationInfo.CATEGORY_MAPS -> return AppInfo.Category.NAVIGATION
                    ApplicationInfo.CATEGORY_NEWS -> return AppInfo.Category.NEWS
                    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> return AppInfo.Category.MULTIMEDIA
                }
            }
        } catch (e: Exception) {}

        // Fallback mapping based on package keywords
        val lowerPkg = packageName.lowercase()
        return when {
            lowerPkg.contains("game") || lowerPkg.contains("arcade") -> AppInfo.Category.GAME
            lowerPkg.contains("social") || lowerPkg.contains("facebook") || lowerPkg.contains("twitter") || lowerPkg.contains("instagram") -> AppInfo.Category.SOCIAL
            lowerPkg.contains("chat") || lowerPkg.contains("messenger") || lowerPkg.contains("whatsapp") || lowerPkg.contains("messaging") || lowerPkg.contains("mail") || lowerPkg.contains("email") -> AppInfo.Category.COMMUNICATION
            lowerPkg.contains("office") || lowerPkg.contains("calc") || lowerPkg.contains("document") || lowerPkg.contains("productivity") -> AppInfo.Category.PRODUCTIVITY
            lowerPkg.contains("tool") || lowerPkg.contains("util") || lowerPkg.contains("settings") || lowerPkg.contains("cleaner") -> AppInfo.Category.TOOLS
            lowerPkg.contains("music") || lowerPkg.contains("player") || lowerPkg.contains("video") || lowerPkg.contains("gallery") || lowerPkg.contains("photo") -> AppInfo.Category.MULTIMEDIA
            lowerPkg.contains("map") || lowerPkg.contains("nav") || lowerPkg.contains("gps") -> AppInfo.Category.NAVIGATION
            lowerPkg.contains("news") || lowerPkg.contains("article") || lowerPkg.contains("reader") -> AppInfo.Category.NEWS
            else -> AppInfo.Category.OTHER
        }
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getRecentApps(limit: Int = 12): List<AppInfo> {
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        val showRecents = prefs.getBoolean("show_recent_apps", true)
        if (!showRecents) return emptyList()

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val recentApps = mutableListOf<AppInfo>()

        try {
            @Suppress("DEPRECATION")
            val tasks = am.getRecentTasks(limit, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
            tasks.forEach { task ->
                val resolveInfo = packageManager.resolveActivity(task.baseIntent, 0)
                if (resolveInfo != null && resolveInfo.activityInfo.packageName != context.packageName) {
                    val appInfo = AppInfo(
                        label = resolveInfo.loadLabel(packageManager).toString(),
                        packageName = resolveInfo.activityInfo.packageName,
                        icon = resolveInfo.loadIcon(packageManager)
                    )
                    appInfo.snapshot = getTaskSnapshot(task.persistentId)
                    recentApps.add(appInfo)
                }
            }
        } catch (e: Exception) {
            Log.e("AppManager", "Error getting recent tasks", e)
        }

        if (recentApps.isEmpty() && hasUsageStatsPermission()) {
            recentApps.addAll(getRecentAppsViaUsageStats(limit))
        }

        return recentApps
    }

    fun refreshRecents() {
        val apps = getRecentApps()
        updateListener?.onRecentsUpdated(apps)
    }

    private fun getRecentAppsViaUsageStats(limit: Int): List<AppInfo> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 60 * 24 // last 24h
        
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val recentApps = mutableListOf<AppInfo>()

        if (stats != null && stats.isNotEmpty()) {
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

    private fun getTaskSnapshot(taskId: Int): Bitmap? {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val getTaskSnapshotMethod = am.javaClass.getMethod("getTaskSnapshot", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            val taskSnapshot = getTaskSnapshotMethod.invoke(am, taskId, false)
            
            if (taskSnapshot != null) {
                val getSnapshotMethod = taskSnapshot.javaClass.getMethod("getSnapshot")
                val buffer = getSnapshotMethod.invoke(taskSnapshot)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && buffer is android.hardware.HardwareBuffer) {
                    return Bitmap.wrapHardwareBuffer(buffer, null)
                } else if (buffer is Bitmap) {
                    return buffer
                }
            }
        } catch (e: Exception) { }
        return null
    }
}
