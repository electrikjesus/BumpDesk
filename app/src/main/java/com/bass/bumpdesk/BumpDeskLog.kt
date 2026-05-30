package com.bass.bumpdesk

import android.util.Log

/**
 * Centralized logcat tags for BumpDesk. Filter logcat with:
 *   adb logcat -s "BumpDesk:IconGroup" "BumpDesk:Theme" ...
 */
object BumpDeskLog {
    const val PREFIX = "BumpDesk"

    /** Disable in JVM unit tests where android.util.Log is unavailable. */
    @JvmField
    var logEnabled: Boolean = true

    object Tag {
        const val ICON_GROUP = "$PREFIX:IconGroup"
        const val THEME = "$PREFIX:Theme"
        const val RECENTS = "$PREFIX:Recents"
        const val GESTURE = "$PREFIX:Gesture"
        const val CAMERA = "$PREFIX:Camera"
        const val WALLPAPER = "$PREFIX:Wallpaper"
        const val RADIAL_MENU = "$PREFIX:RadialMenu"
        const val CORE = "$PREFIX:Core"
    }

    private fun write(level: Int, tag: String, message: String, throwable: Throwable? = null) {
        if (!logEnabled) return
        try {
            when (level) {
                Log.DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
                Log.INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
                Log.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
                Log.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            }
        } catch (_: Throwable) {
            // android.util.Log throws on JVM unit tests without Robolectric.
        }
    }

    fun d(tag: String, op: String, message: String = "") {
        write(Log.DEBUG, tag, format(op, message))
    }

    fun i(tag: String, op: String, message: String = "") {
        write(Log.INFO, tag, format(op, message))
    }

    fun w(tag: String, op: String, message: String = "", throwable: Throwable? = null) {
        write(Log.WARN, tag, format(op, message), throwable)
    }

    fun e(tag: String, op: String, message: String = "", throwable: Throwable? = null) {
        write(Log.ERROR, tag, format(op, message), throwable)
    }

    fun enter(tag: String, op: String, details: String = "") {
        d(tag, op, "enter${detailSuffix(details)}")
    }

    fun exit(tag: String, op: String, details: String = "") {
        d(tag, op, "exit${detailSuffix(details)}")
    }

    fun fail(tag: String, op: String, message: String, throwable: Throwable? = null) {
        e(tag, op, "FAIL | $message", throwable)
    }

    private fun detailSuffix(details: String): String =
        if (details.isEmpty()) "" else " | $details"

    private fun format(op: String, message: String): String =
        if (message.isEmpty()) "[$op]" else "[$op] $message"
}
