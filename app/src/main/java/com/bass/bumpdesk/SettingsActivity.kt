package com.bass.bumpdesk

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        
        // General Toggles
        findViewById<CheckBox>(R.id.cbShowRecentApps).apply {
            isChecked = prefs.getBoolean("show_recent_apps", true)
            setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("show_recent_apps", isChecked).apply() }
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

        findViewById<Button>(R.id.btnClearCache).setOnClickListener {
            // Logic to clear cache usually involves resetting textures in renderer
            // For now we just clear prefs and notify
            prefs.edit().clear().apply()
            Toast.makeText(this, "Cache cleared. Some changes require restart.", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnResetDefaults).setOnClickListener {
            prefs.edit().apply {
                putInt("physics_friction", 94)
                putInt("physics_bounciness", 25)
                putInt("physics_gravity", 10)
                putInt("layout_item_scale", 50)
                putInt("layout_grid_spacing", 60)
                apply()
            }
            recreate()
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
}
