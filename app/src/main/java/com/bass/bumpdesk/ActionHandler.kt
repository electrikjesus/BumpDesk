package com.bass.bumpdesk

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.opengl.GLSurfaceView
import android.util.Log

class ActionHandler(private val context: Context, private val glSurfaceView: GLSurfaceView, private val renderer: BumpRenderer) {

    fun launchApp(item: BumpItem, windowingMode: Int = LauncherActivity.WINDOWING_MODE_UNDEFINED) {
        val appInfo = item.appInfo ?: return
        val packageName = appInfo.packageName
        val className = appInfo.className
        val taskId = appInfo.taskId
        val savedIntent = appInfo.intent
        
        Log.d("ActionHandler", "Launching app: $packageName, class: $className, taskId: $taskId, mode: $windowingMode")

        val options = ActivityOptions.makeBasic()
        if (windowingMode != LauncherActivity.WINDOWING_MODE_UNDEFINED) {
            try {
                val setLaunchWindowingModeMethod = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                setLaunchWindowingModeMethod.invoke(options, windowingMode)
                
                if (windowingMode == LauncherActivity.WINDOWING_MODE_FREEFORM) {
                    val dm = context.resources.displayMetrics
                    val rect = Rect(dm.widthPixels/4, dm.heightPixels/4, dm.widthPixels*3/4, dm.heightPixels*3/4)
                    val setLaunchBoundsMethod = ActivityOptions::class.java.getMethod("setLaunchBounds", Rect::class.java)
                    setLaunchBoundsMethod.invoke(options, rect)
                }
            } catch (e: Exception) {
                Log.w("ActionHandler", "Failed to set windowing mode: ${e.message}")
            }
        }

        // 1. Try moving existing task to front if we have a taskId
        if (taskId != -1 && windowingMode == LauncherActivity.WINDOWING_MODE_UNDEFINED) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            try {
                am.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME, options.toBundle())
                return
            } catch (e: Exception) {
                Log.w("ActionHandler", "Failed to move task to front: ${e.message}")
            }
        }

        // 2. Try using the saved intent from Recents if available
        if (savedIntent != null) {
            try {
                savedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(savedIntent, options.toBundle())
                return
            } catch (e: Exception) {
                Log.w("ActionHandler", "Failed to start activity with saved intent: ${e.message}")
            }
        }

        // 3. Fallback to constructing an intent from package/class name
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
            Log.e("ActionHandler", "Could not create intent for $packageName")
            return
        }
        
        try {
            context.startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Log.e("ActionHandler", "Failed to start activity: ${e.message}")
            // Final fallback to basic launch intent
            val fallbackIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (fallbackIntent != null) {
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
            }
        }
    }

    fun removeTask(taskId: Int) {
        if (taskId == -1) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            val removeTaskMethod = am.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
            removeTaskMethod.invoke(am, taskId)
            Log.d("ActionHandler", "Removed task: $taskId")
        } catch (e: Exception) {
            Log.w("ActionHandler", "Failed to remove task $taskId: ${e.message}")
            // Fallback for non-privileged apps or different Android versions
            try {
                @Suppress("DEPRECATION")
                am.getRecentTasks(100, ActivityManager.RECENT_IGNORE_UNAVAILABLE).find { it.persistentId == taskId }?.let {
                   // Some versions might allow it via hidden activity manager if we are system app
                }
            } catch (ex: Exception) {}
        }
    }

    fun handleIntent(intent: Intent, onShowResetButton: (Boolean) -> Unit) {
        if (intent.action == LauncherActivity.ACTION_RECENTS) {
            glSurfaceView.queueEvent {
                renderer.sceneState.recentsPile?.let {
                    renderer.camera.focusOnWall(CameraManager.ViewMode.BACK_WALL, floatArrayOf(0f, 4f, 2f), floatArrayOf(0f, 4f, -renderer.ROOM_SIZE))
                    onShowResetButton(true)
                }
            }
        }
    }
}
