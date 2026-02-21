package com.bass.bumpdesk

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.opengl.GLSurfaceView

class ActionHandler(private val context: Context, private val glSurfaceView: GLSurfaceView, private val renderer: BumpRenderer) {

    fun launchApp(item: BumpItem, windowingMode: Int = LauncherActivity.WINDOWING_MODE_UNDEFINED) {
        val packageName = item.appInfo?.packageName ?: return
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
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
                if (windowingMode == LauncherActivity.WINDOWING_MODE_FREEFORM) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
            }
        }
        context.startActivity(intent, options.toBundle())
        // Update recents is usually called by the activity
    }

    fun handleIntent(intent: Intent, onShowResetButton: (Boolean) -> Unit) {
        if (intent.action == LauncherActivity.ACTION_RECENTS) {
            glSurfaceView.queueEvent {
                renderer.sceneState.recentsPile?.let {
                    renderer.camera.focusOnWall(CameraManager.ViewMode.BACK_WALL, floatArrayOf(0f, 4f, 2f), floatArrayOf(0f, 4f, -10f))
                    onShowResetButton(true)
                }
            }
        }
    }
}
