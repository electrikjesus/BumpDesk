package com.bass.bumpdesk

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

class ActionHandler(private val context: Context, private val glSurfaceView: android.opengl.GLSurfaceView, private val renderer: BumpRenderer) {

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)

    fun launchApp(
        item: BumpItem,
        windowingMode: Int = LauncherActivity.WINDOWING_MODE_UNDEFINED,
        rememberMode: Boolean = true,
    ) {
        val appInfo = item.appInfo ?: return
        val packageName = appInfo.packageName
        val className = appInfo.className
        val savedIntent = appInfo.intent

        val explicitMode = AppLaunchUtils.windowingModeToLaunchMode(windowingMode)
        val launchMode = LaunchPreferences.resolveLaunchMode(prefs, packageName, explicitMode)

        if (rememberMode && explicitMode != null && LaunchPreferences.rememberLaunchMode(prefs)) {
            LaunchPreferences.savePackageLaunchMode(prefs, packageName, launchMode)
        }

        BumpDeskLog.i(
            BumpDeskLog.Tag.LAUNCH,
            "launchApp",
            "pkg=$packageName mode=$launchMode explicit=$explicitMode",
        )

        AppLaunchUtils.prepareFreeformLaunch(context, launchMode) {
            launchAppInternal(packageName, className, savedIntent, launchMode)
        }
    }

    private fun launchAppInternal(
        packageName: String,
        className: String?,
        savedIntent: Intent?,
        launchMode: String,
    ) {
        val options = AppLaunchUtils.makeActivityOptions(context, launchMode, prefs)
        val optionsBundle = options.toBundle()

        if (savedIntent != null) {
            try {
                savedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(savedIntent, optionsBundle)
                notifyFreeformLaunchDone(context)
                return
            } catch (e: Exception) {
                BumpDeskLog.w(BumpDeskLog.Tag.LAUNCH, "launchApp", "saved intent failed: ${e.message}")
            }
        }

        val intent = if (className != null) {
            Intent().apply {
                component = ComponentName(packageName, className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        if (intent == null) {
            BumpDeskLog.fail(BumpDeskLog.Tag.LAUNCH, "launchApp", "no intent for $packageName")
            return
        }

        try {
            context.startActivity(intent, optionsBundle)
            notifyFreeformLaunchDone(context)
        } catch (e: Exception) {
            BumpDeskLog.fail(BumpDeskLog.Tag.LAUNCH, "launchApp", "startActivity failed: ${e.message}", e)
            val fallbackIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (fallbackIntent != null) {
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(fallbackIntent, optionsBundle)
                    notifyFreeformLaunchDone(context)
                } catch (e2: Exception) {
                    BumpDeskLog.fail(BumpDeskLog.Tag.LAUNCH, "launchApp", "fallback failed: ${e2.message}", e2)
                }
            }
        }
    }

    private fun notifyFreeformLaunchDone(context: Context) {
        if (!FreeformHackHelper.isFreeformHackActive) return
        context.sendBroadcast(Intent(InvisibleActivityFreeform.ACTION_FREEFORM_LAUNCH_DONE))
    }

    fun removeTask(taskId: Int) {
        if (taskId == -1) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            val removeTaskMethod = am.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
            removeTaskMethod.invoke(am, taskId)
            BumpDeskLog.d(BumpDeskLog.Tag.LAUNCH, "removeTask", "taskId=$taskId")
        } catch (e: Exception) {
            BumpDeskLog.w(BumpDeskLog.Tag.LAUNCH, "removeTask", "failed taskId=$taskId: ${e.message}")
        }
    }

    fun minimizeTask(taskId: Int) {
        if (taskId == -1) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            val moveTaskToBack = am.javaClass.getMethod(
                "moveTaskToBack",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            moveTaskToBack.invoke(am, taskId, false)
            BumpDeskLog.d(BumpDeskLog.Tag.LAUNCH, "minimizeTask", "taskId=$taskId")
        } catch (e: Exception) {
            BumpDeskLog.w(BumpDeskLog.Tag.LAUNCH, "minimizeTask", "failed taskId=$taskId: ${e.message}")
        }
    }

    fun handleIntent(intent: Intent, onShowResetButton: (Boolean) -> Unit) {
        if (intent.action == LauncherActivity.ACTION_RECENTS) {
            glSurfaceView.queueEvent {
                renderer.focusRecentsPile(onShowResetButton)
            }
        }
    }
}
