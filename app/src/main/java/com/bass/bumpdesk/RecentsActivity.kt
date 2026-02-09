package com.bass.bumpdesk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity intended to act as the Recents Provider surface in AOSP.
 * It will focus on the Recents Pile/Widget in the 3D scene.
 */
class RecentsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This activity could either host its own GLSurfaceView 
        // or tell the LauncherActivity to focus on the recents pile.
        // For AOSP integration, we might want this to be a separate entry point.
        
        // For now, let's just launch LauncherActivity with a specific flag
        // or intent action that tells it to zoom into recents.
        val intent = android.content.Intent(this, LauncherActivity::class.java).apply {
            action = "com.bass.bumpdesk.ACTION_RECENTS"
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }
}
