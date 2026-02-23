package com.bass.bumpdesk

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class OnboardingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        findViewById<Button>(R.id.btnSetDefaultLauncher).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (e: Exception) {
                // Fallback for older Android versions or custom ROMs
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                    Toast.makeText(this, "Go to 'Apps' -> 'Default Apps' to set BumpDesk as Home", Toast.LENGTH_LONG).show()
                } catch (e2: Exception) {
                    Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnGetStarted).setOnClickListener {
            val appManager = AppManager(this)
            
            if (!appManager.hasUsageStatsPermission()) {
                showPermissionPrompt()
            } else {
                completeOnboarding()
            }
        }
    }

    private fun showPermissionPrompt() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("BumpDesk requires 'Usage Access' to provide the Recents Pile and other desktop features. Please enable it in the next screen.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    startActivityForResult(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), 1001)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
                    completeOnboarding()
                }
            }
            .setNegativeButton("Skip") { _, _ ->
                completeOnboarding()
            }
            .setCancelable(false)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        val prefs = getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_complete", true).apply()
        startActivity(Intent(this, LauncherActivity::class.java))
        finish()
    }
}
