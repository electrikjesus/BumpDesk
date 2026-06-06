package com.bass.bumpdesk

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

/**
 * Tiny off-screen freeform activity that keeps the device in a freeform workspace.
 * Ported from Taskbar's InvisibleActivityFreeform.
 */
class InvisibleActivityFreeform : Activity() {

    private var initialLaunch = true
    private var proceedWithOnCreate = true
    private var finishing = false

    private val launchDoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            moveHackToBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FreeformHackHelper.isFreeformHackActive) {
            proceedWithOnCreate = false
            super.finish()
            overridePendingTransition(0, 0)
            return
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        )
        FreeformHackHelper.isFreeformHackActive = true
        val filter = IntentFilter(ACTION_FREEFORM_LAUNCH_DONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(launchDoneReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(launchDoneReceiver, filter)
        }
        BumpDeskLog.i(BumpDeskLog.Tag.LAUNCH, "InvisibleActivityFreeform", "hack started")
    }

    override fun onStart() {
        super.onStart()
        FreeformHackHelper.isInFreeformWorkspace = true
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            !isInMultiWindowMode &&
            !initialLaunch
        ) {
            reallyFinish()
        }
        initialLaunch = false
    }

    override fun onStop() {
        super.onStop()
        if (!finishing) {
            FreeformHackHelper.isInFreeformWorkspace = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!proceedWithOnCreate) return
        if (!finishing) {
            try {
                unregisterReceiver(launchDoneReceiver)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    /** Taskbar keeps this activity alive; only reallyFinish() tears it down. */
    override fun finish() {
    }

    private fun moveHackToBack() {
        if (finishing) return
        BumpDeskLog.d(BumpDeskLog.Tag.LAUNCH, "InvisibleActivityFreeform", "moveTaskToBack")
        moveTaskToBack(true)
    }

    private fun reallyFinish() {
        if (finishing) return
        finishing = true
        try {
            unregisterReceiver(launchDoneReceiver)
        } catch (_: IllegalArgumentException) {
        }
        super.finish()
        overridePendingTransition(0, 0)
        cleanup()
    }

    private fun cleanup() {
        FreeformHackHelper.reset()
        BumpDeskLog.i(BumpDeskLog.Tag.LAUNCH, "InvisibleActivityFreeform", "hack stopped")
    }

    companion object {
        const val ACTION_FREEFORM_LAUNCH_DONE = "com.bass.bumpdesk.action.FREEFORM_LAUNCH_DONE"
    }
}
