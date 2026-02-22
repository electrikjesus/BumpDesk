package com.bass.bumpdesk

import android.os.Handler
import android.os.HandlerThread

class PhysicsThread(
    private val sceneState: SceneState,
    private val physicsEngine: PhysicsEngine,
    private val onBump: (Float) -> Unit
) : HandlerThread("PhysicsThread") {

    private var handler: Handler? = null
    private var isRunning = false
    private val frameTimeMs = 16L // ~60 FPS

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                physicsEngine.update(
                    sceneState.bumpItems,
                    sceneState.piles,
                    sceneState.selectedItem,
                    onBump
                )
                handler?.postDelayed(this, frameTimeMs)
            }
        }
    }

    override fun start() {
        super.start()
        handler = Handler(looper)
        isRunning = true
        handler?.post(updateRunnable)
    }

    fun stopPhysics() {
        isRunning = false
        quitSafely()
    }
}
