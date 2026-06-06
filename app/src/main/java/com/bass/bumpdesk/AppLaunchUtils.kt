package com.bass.bumpdesk

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.math.max

/** SmartDock/Taskbar-style freeform bounds, hidden API access, and workspace hack. */
object AppLaunchUtils {

    private var reflectionAllowed = false

    fun init(context: Context) {
        allowHiddenApiReflection()
        BumpDeskLog.i(
            BumpDeskLog.Tag.LAUNCH,
            "init",
            "freeformSupport=${hasFreeformSupport(context)} reflection=$reflectionAllowed",
        )
    }

    fun windowingModeToLaunchMode(windowingMode: Int): String? = when (windowingMode) {
        LauncherActivity.WINDOWING_MODE_UNDEFINED -> null
        LauncherActivity.WINDOWING_MODE_FULLSCREEN -> "fullscreen"
        LauncherActivity.WINDOWING_MODE_PINNED -> "pinned"
        LauncherActivity.WINDOWING_MODE_FREEFORM -> "standard"
        else -> null
    }

    fun launchModeUsesFreeform(mode: String): Boolean =
        mode != "fullscreen" && mode != "pinned"

    fun hasFreeformSupport(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
            Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) != 0
    }

    fun allowHiddenApiReflection() {
        if (reflectionAllowed) return
        try {
            HiddenApiBypass.addHiddenApiExemptions("")
            reflectionAllowed = true
            BumpDeskLog.i(BumpDeskLog.Tag.LAUNCH, "allowHiddenApiReflection", "ok")
        } catch (e: Exception) {
            BumpDeskLog.w(BumpDeskLog.Tag.LAUNCH, "allowHiddenApiReflection", "failed: ${e.message}")
        }
    }

    fun makeLaunchBounds(
        context: Context,
        mode: String,
        dockHeight: Int = 0,
        displayId: Int = Display.DEFAULT_DISPLAY,
        scaleFactor: Float = LaunchPreferences.scaleFactor(
            context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE),
        ),
    ): Rect {
        val dm = context.resources.displayMetrics
        val deviceWidth = dm.widthPixels
        val deviceHeight = dm.heightPixels
        val statusBarHeight = statusBarHeightPx(context)
        val navHeight = navigationBarHeightPx(context)
        val usableHeight = if (shouldApplyNavbarFix()) {
            deviceHeight - max(dockHeight, navHeight) - statusBarHeight
        } else {
            deviceHeight - dockHeight - statusBarHeight
        }

        var left = 0
        var top = 0
        var right = 0
        var bottom = 0
        when (mode) {
            "standard" -> {
                left = (deviceWidth / (5 * scaleFactor)).toInt()
                top = ((usableHeight + statusBarHeight) / (7 * scaleFactor)).toInt()
                right = deviceWidth - left
                bottom = usableHeight + dockHeight - top
            }
            "maximized" -> {
                right = deviceWidth
                bottom = usableHeight
            }
            "portrait" -> {
                left = deviceWidth / 3
                top = usableHeight / 15
                right = deviceWidth - left
                bottom = usableHeight + dockHeight - top
            }
            "tiled-left" -> {
                right = deviceWidth / 2
                bottom = usableHeight
            }
            "tiled-top" -> {
                right = deviceWidth
                bottom = (usableHeight + statusBarHeight) / 2
            }
            "tiled-right" -> {
                left = deviceWidth / 2
                right = deviceWidth
                bottom = usableHeight
            }
            "tiled-bottom" -> {
                right = deviceWidth
                top = (usableHeight + statusBarHeight) / 2
                bottom = usableHeight + statusBarHeight
            }
            else -> {
                left = (deviceWidth / (5 * scaleFactor)).toInt()
                top = ((usableHeight + statusBarHeight) / (7 * scaleFactor)).toInt()
                right = deviceWidth - left
                bottom = usableHeight + dockHeight - top
            }
        }
        return Rect(left, top, right, bottom)
    }

    fun makeActivityOptions(
        context: Context,
        mode: String,
        prefs: SharedPreferences = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE),
        dockHeight: Int = 0,
        displayId: Int = Display.DEFAULT_DISPLAY,
        freeformHackBounds: Rect? = null,
    ): ActivityOptions {
        allowHiddenApiReflection()

        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = displayId

        val windowMode = when (mode) {
            "fullscreen" -> LauncherActivity.WINDOWING_MODE_FULLSCREEN
            "pinned" -> LauncherActivity.WINDOWING_MODE_PINNED
            else -> LauncherActivity.WINDOWING_MODE_FREEFORM
        }

        if (windowMode == LauncherActivity.WINDOWING_MODE_FREEFORM) {
            options.launchBounds = freeformHackBounds ?: makeLaunchBounds(
                context,
                mode,
                dockHeight,
                displayId,
                LaunchPreferences.scaleFactor(prefs),
            )
        }

        try {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType,
            )
            method.invoke(options, windowMode)
            BumpDeskLog.d(
                BumpDeskLog.Tag.LAUNCH,
                "makeActivityOptions",
                "mode=$mode windowMode=$windowMode bounds=${options.launchBounds}",
            )
        } catch (e: Exception) {
            BumpDeskLog.w(BumpDeskLog.Tag.LAUNCH, "makeActivityOptions", "setLaunchWindowingMode failed: ${e.message}")
        }
        return options
    }

    /** Starts a 1px off-screen freeform activity to activate the freeform workspace (Taskbar pattern). */
    fun startFreeformHack(context: Context) {
        if (!hasFreeformSupport(context)) return
        if (FreeformHackHelper.isFreeformHackActive) return

        val intent = Intent(context, InvisibleActivityFreeform::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
        }
        startActivityLowerRight(context, intent)
    }

    fun prepareFreeformLaunch(context: Context, launchMode: String, onReady: () -> Unit) {
        val runnable = Runnable { runOnMain(onReady) }
        if (!launchModeUsesFreeform(launchMode) || !hasFreeformSupport(context)) {
            runnable.run()
            return
        }
        if (FreeformHackHelper.isFreeformHackActive && FreeformHackHelper.isInFreeformWorkspace) {
            runnable.run()
            return
        }
        runOnMain {
            startFreeformHack(context)
            val delayMs = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) 300L else 100L
            Handler(Looper.getMainLooper()).postDelayed(runnable, delayMs)
        }
    }

    private fun startActivityLowerRight(context: Context, intent: Intent) {
        val dm = context.resources.displayMetrics
        val width = dm.widthPixels
        val height = dm.heightPixels
        val bounds = Rect(width, height, width + 1, height + 1)
        val options = makeActivityOptions(context, "standard", freeformHackBounds = bounds)
        try {
            context.startActivity(intent, options.toBundle())
            BumpDeskLog.i(BumpDeskLog.Tag.LAUNCH, "startActivityLowerRight", "bounds=$bounds")
        } catch (e: Exception) {
            BumpDeskLog.w(BumpDeskLog.Tag.LAUNCH, "startFreeformHack", "failed: ${e.message}")
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            Handler(Looper.getMainLooper()).post(block)
        }
    }

    private fun statusBarHeightPx(context: Context): Int {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }

    private fun navigationBarHeightPx(context: Context): Int {
        val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }

    private fun shouldApplyNavbarFix(): Boolean =
        Build.VERSION.SDK_INT > Build.VERSION_CODES.S && isNavbarEnabled()

    private fun isNavbarEnabled(): Boolean {
        return try {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, "qemu.hw.mainkeys") != "1"
        } catch (_: Exception) {
            true
        }
    }
}
