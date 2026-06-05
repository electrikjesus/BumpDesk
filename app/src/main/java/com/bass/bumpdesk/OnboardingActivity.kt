package com.bass.bumpdesk

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class OnboardingActivity : AppCompatActivity() {

    private val usageAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        completeOnboarding()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        findViewById<Button>(R.id.btnSetDefaultLauncher).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                    Toast.makeText(this, "Go to 'Apps' -> 'Default Apps' to set BumpDesk as Home", Toast.LENGTH_LONG).show()
                } catch (e2: Exception) {
                    Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnGetStarted).setOnClickListener {
            beginSetup()
        }
    }

    private fun beginSetup() {
        val appManager = AppManager(this)
        if (!appManager.hasUsageStatsPermission()) {
            showUsageStatsPrompt()
        } else {
            completeOnboarding()
        }
    }

    private fun showUsageStatsPrompt() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Usage Access")
            .setMessage("BumpDesk needs Usage Access to show recent apps in the Recents pile. You can enable this on the next screen.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    usageAccessLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
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

    private fun completeOnboarding() {
        val prefs = getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_complete", true).apply()
        startActivity(Intent(this, LauncherActivity::class.java))
        finish()
    }
}
