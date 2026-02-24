package com.bass.bumpdesk

import android.content.Context
import android.os.*
import android.util.Log

class HapticManager(private val context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Light tick for scrolling or subtle interactions.
     */
    fun tick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    /**
     * Heavy impact for collisions.
     */
    fun heavyImpact(magnitude: Float) {
        val duration = (magnitude * 50).toLong().coerceIn(10, 100)
        val amplitude = (magnitude * 255).toInt().coerceIn(50, 255)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    /**
     * Standard selection click.
     */
    fun selection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            tick()
        }
    }

    /**
     * Double-click or secondary action haptic.
     */
    fun doubleClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            tick()
            Handler(Looper.getMainLooper()).postDelayed({ tick() }, 50)
        }
    }

    /**
     * Haptic feedback for hitting a room boundary (e.g., camera limit).
     */
    fun boundaryLimit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            heavyImpact(0.6f)
        }
    }

    /**
     * Subtle "texture" feel when dragging across surfaces.
     */
    fun surfaceTexture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(5, 30))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(5)
        }
    }
}
