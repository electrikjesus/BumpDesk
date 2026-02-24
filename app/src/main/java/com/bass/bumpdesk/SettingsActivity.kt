package com.bass.bumpdesk

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var appManager: AppManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        appManager = AppManager(this)
        val prefs = getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        
        // General Toggles
        findViewById<CheckBox>(R.id.cbShowRecentApps).apply {
            isChecked = prefs.getBoolean("show_recent_apps", true)
            setOnCheckedChangeListener { _, isChecked -> 
                if (isChecked && !appManager.hasUsageStatsPermission()) {
                    showUsageAccessDialog()
                }
                prefs.edit().putBoolean("show_recent_apps", isChecked).apply() 
            }
        }

        findViewById<CheckBox>(R.id.cbShowAppDrawerIcon).apply {
            isChecked = prefs.getBoolean("show_app_drawer_icon", true)
            setOnCheckedChangeListener { _, isChecked -> 
                prefs.edit().putBoolean("show_app_drawer_icon", isChecked).apply() 
            }
        }

        findViewById<CheckBox>(R.id.cbInfiniteDesktop).apply {
            isChecked = prefs.getBoolean("infinite_desktop_mode", false)
            setOnCheckedChangeListener { _, isChecked -> 
                prefs.edit().putBoolean("infinite_desktop_mode", isChecked).apply() 
            }
        }

        findViewById<CheckBox>(R.id.cbUseWallpaperAsFloor).apply {
            isChecked = prefs.getBoolean("use_wallpaper_as_floor", false)
            setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("use_wallpaper_as_floor", isChecked).apply() }
        }

        // Theme Selection
        val themes = ThemeManager.getThemeList(this)
        findViewById<Button>(R.id.btnChangeTheme).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Theme")
                .setItems(themes.toTypedArray()) { _, which ->
                    val selected = themes[which]
                    prefs.edit().putString("selected_theme", selected).apply()
                    ThemeManager.setTheme(selected, this@SettingsActivity)
                    Toast.makeText(this@SettingsActivity, "Theme set to: $selected", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        // Physics Tuning
        setupSeekBar(R.id.sbFriction, "physics_friction", 94, 100)
        setupSeekBar(R.id.sbBounciness, "physics_bounciness", 25, 100)
        setupSeekBar(R.id.sbGravity, "physics_gravity", 10, 100)

        // Layout & Scaling
        setupSeekBar(R.id.sbItemScale, "layout_item_scale", 50, 100)
        setupSeekBar(R.id.sbGridSpacing, "layout_grid_spacing", 60, 100)
        setupSeekBar(R.id.sbRoomSize, "room_size_scale", 30, 100)

        findViewById<Button>(R.id.btnResetCamera).setOnClickListener {
            prefs.edit().apply {
                remove("cam_def_pos_x"); remove("cam_def_pos_y"); remove("cam_def_pos_z")
                remove("cam_def_lat_x"); remove("cam_def_lat_y"); remove("cam_def_lat_z")
                // Trigger a change notification
                putBoolean("reset_camera_trigger", true)
                apply()
            }
            Toast.makeText(this, "Camera defaults restored.", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnClearCache).setOnClickListener {
            // Only clear theme and state, not ALL preferences
            prefs.edit().apply {
                remove("onboarding_complete")
                remove("selected_theme")
                // Clear any other temporary state but keep settings
                apply()
            }
            Toast.makeText(this, "Cache cleared. Some changes require restart.", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnResetDefaults).setOnClickListener {
            prefs.edit().apply {
                putInt("physics_friction", 94)
                putInt("physics_bounciness", 25)
                putInt("physics_gravity", 10)
                putInt("layout_item_scale", 50)
                putInt("layout_grid_spacing", 60)
                putInt("room_size_scale", 30)
                putBoolean("show_recent_apps", true)
                putBoolean("show_app_drawer_icon", true)
                putBoolean("infinite_desktop_mode", false)
                putBoolean("use_wallpaper_as_floor", false)
                apply()
            }
            recreate()
        }

        // Apply and Exit
        findViewById<Button>(R.id.btnApplyChanges).setOnClickListener {
            finish()
        }
    }

    private fun setupSeekBar(resId: Int, prefKey: String, defaultValue: Int, max: Int) {
        val prefs = getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        findViewById<SeekBar>(resId).apply {
            this.max = max
            progress = prefs.getInt(prefKey, defaultValue)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) prefs.edit().putInt(prefKey, progress).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun showUsageAccessDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Usage Access Required")
            .setMessage("To display recent apps, BumpDesk needs 'Usage Access' permission. This is used only to find your most recently used applications.")
            .setPositiveButton("Settings") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                findViewById<CheckBox>(R.id.cbShowRecentApps).isChecked = false
            }
            .show()
    }
}
