package com.bass.bumpdesk

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        
        // Recent Apps Toggle
        val cbShowRecentApps = findViewById<CheckBox>(R.id.cbShowRecentApps)
        cbShowRecentApps.isChecked = prefs.getBoolean("show_recent_apps", true)
        cbShowRecentApps.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_recent_apps", isChecked).apply()
        }

        // Use Wallpaper as Floor Toggle
        val cbUseWallpaperAsFloor = findViewById<CheckBox>(R.id.cbUseWallpaperAsFloor)
        cbUseWallpaperAsFloor.isChecked = prefs.getBoolean("use_wallpaper_as_floor", false)
        cbUseWallpaperAsFloor.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("use_wallpaper_as_floor", isChecked).apply()
        }

        // Theme Selection
        val themes = ThemeManager.getThemeList(this)
        findViewById<Button>(R.id.btnChangeTheme).setOnClickListener {
            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle("Select Theme")
            builder.setItems(themes.toTypedArray()) { _, which ->
                val selected = themes[which]
                prefs.edit().putString("selected_theme", selected).apply()
                ThemeManager.setTheme(selected, this@SettingsActivity)
                Toast.makeText(this@SettingsActivity, "Theme set to: $selected", Toast.LENGTH_SHORT).show()
            }
            builder.show()
        }

        findViewById<Button>(R.id.btnClearCache).setOnClickListener {
            prefs.edit().clear().apply()
            Toast.makeText(this, "Preferences and cache cleared. Restarting...", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
