package com.bass.bumpdesk

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {
    private lateinit var appManager: AppManager
    private var pendingWallpaperCheckbox: CheckBox? = null

    private val wallpaperPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val checkbox = pendingWallpaperCheckbox ?: findViewById(R.id.cbUseWallpaperAsFloor)
        if (!WallpaperPermissions.permissionsGranted(results)) {
            resetWallpaperCheckbox(checkbox)
            Toast.makeText(
                this,
                "Photos/media permission is required to use the system wallpaper on the floor.",
                Toast.LENGTH_LONG
            ).show()
            pendingWallpaperCheckbox = null
            return@registerForActivityResult
        }
        continueWallpaperSetup(checkbox)
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val checkbox = pendingWallpaperCheckbox ?: return@registerForActivityResult
        if (granted) {
            enableWallpaperFloorPref(checkbox)
        } else {
            showWallpaperFallbackDialog(checkbox)
        }
        pendingWallpaperCheckbox = null
    }

    private val pickWallpaperLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val checkbox = pendingWallpaperCheckbox
        pendingWallpaperCheckbox = null
        if (uri == null) {
            resetWallpaperCheckbox(checkbox ?: findViewById(R.id.cbUseWallpaperAsFloor))
            return@registerForActivityResult
        }
        if (WallpaperFloorProvider.savePickedWallpaper(this, uri)) {
            getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("use_wallpaper_as_floor", true).apply()
            checkbox?.isChecked = true
            Toast.makeText(this, "Wallpaper floor enabled.", Toast.LENGTH_SHORT).show()
        } else {
            resetWallpaperCheckbox(checkbox ?: findViewById(R.id.cbUseWallpaperAsFloor))
            Toast.makeText(this, "Could not load the selected image.", Toast.LENGTH_LONG).show()
        }
    }

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
            setOnCheckedChangeListener { button, isChecked ->
                if (!isChecked) {
                    prefs.edit().putBoolean("use_wallpaper_as_floor", false).apply()
                    WallpaperFloorProvider.clear()
                    WallpaperFloorProvider.clearPickedWallpaper(this@SettingsActivity)
                    return@setOnCheckedChangeListener
                }
                beginWallpaperSetup(button as CheckBox)
            }
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

        // Physics Tuning with Real-time value updates
        setupSeekBar(R.id.sbFriction, R.id.tvFrictionVal, "physics_friction", 94, 100, "%")
        setupSeekBar(R.id.sbBounciness, R.id.tvBouncinessVal, "physics_bounciness", 25, 100, "%")
        setupSeekBar(R.id.sbGravity, R.id.tvGravityVal, "physics_gravity", 10, 100, "%")

        // Layout & Scaling with Real-time value updates
        setupSeekBar(R.id.sbItemScale, R.id.tvItemScaleVal, "layout_item_scale", 50, 100, "%")
        setupSeekBar(R.id.sbGridSpacing, R.id.tvGridSpacingVal, "layout_grid_spacing", 60, 100, "%")
        setupSeekBar(R.id.sbRoomSize, R.id.tvRoomSizeVal, "room_size_scale", 30, 100, "")

        findViewById<Button>(R.id.btnResetCamera).setOnClickListener {
            prefs.edit().apply {
                remove("cam_def_pos_x"); remove("cam_def_pos_y"); remove("cam_def_pos_z")
                remove("cam_def_lat_x"); remove("cam_def_lat_y"); remove("cam_def_lat_z")
                putBoolean("reset_camera_trigger", true)
                apply()
            }
            Toast.makeText(this, "Camera defaults restored.", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnClearCache).setOnClickListener {
            prefs.edit().apply {
                remove("onboarding_complete")
                remove("selected_theme")
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
            WallpaperFloorProvider.clearPickedWallpaper(this)
            recreate()
        }

        findViewById<Button>(R.id.btnApplyChanges).setOnClickListener {
            finish()
        }
    }

    private fun beginWallpaperSetup(checkbox: CheckBox) {
        pendingWallpaperCheckbox = checkbox
        if (!WallpaperPermissions.hasAccess(this)) {
            checkbox.isChecked = false
            wallpaperPermissionLauncher.launch(WallpaperPermissions.requiredPermissions())
            return
        }
        continueWallpaperSetup(checkbox)
    }

    private fun continueWallpaperSetup(checkbox: CheckBox) {
        pendingWallpaperCheckbox = checkbox
        if (WallpaperPermissions.needsLegacyStoragePrompt(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
            ) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Storage Permission Needed")
                    .setMessage(
                        "Android also requires legacy Storage permission so BumpDesk can read " +
                            "the system wallpaper file on this device."
                    )
                    .setPositiveButton("Continue") { _, _ ->
                        storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        showWallpaperFallbackDialog(checkbox)
                    }
                    .show()
            } else {
                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            return
        }
        pendingWallpaperCheckbox = null
        enableWallpaperFloorPref(checkbox)
    }

    private fun enableWallpaperFloorPref(checkbox: CheckBox) {
        WallpaperFloorProvider.refreshWithRetry(this) { loaded ->
            if (loaded) {
                getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("use_wallpaper_as_floor", true).apply()
                Toast.makeText(this, "Wallpaper floor enabled.", Toast.LENGTH_SHORT).show()
            } else {
                resetWallpaperCheckbox(checkbox)
                showWallpaperFallbackDialog(checkbox)
            }
        }
    }

    private fun resetWallpaperCheckbox(checkbox: CheckBox) {
        checkbox.isChecked = false
        getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("use_wallpaper_as_floor", false).apply()
    }

    private fun showWallpaperFallbackDialog(checkbox: CheckBox) {
        pendingWallpaperCheckbox = checkbox
        val needsStorage = WallpaperPermissions.needsLegacyStoragePrompt(this)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cannot Read System Wallpaper")
            .setMessage(
                if (needsStorage) {
                    "Photos access is granted, but Android also blocked legacy Storage access " +
                        "needed to read the wallpaper file on this device. Enable Storage in app " +
                        "settings, or pick the same wallpaper image from Photos."
                } else {
                    "Android blocked direct access to the system wallpaper on this device. " +
                        "Pick your wallpaper image from Photos to use it on the floor."
                }
            )
            .setPositiveButton("Pick Image") { _, _ ->
                pickWallpaperLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            .setNeutralButton("App Settings") { _, _ ->
                WallpaperPermissions.openAppSettings(this)
                pendingWallpaperCheckbox = null
            }
            .setNegativeButton("Cancel") { _, _ ->
                pendingWallpaperCheckbox = null
            }
            .show()
    }

    private fun setupSeekBar(resId: Int, valResId: Int, prefKey: String, defaultValue: Int, max: Int, suffix: String) {
        val prefs = getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        val textView = findViewById<TextView>(valResId)
        findViewById<SeekBar>(resId).apply {
            this.max = max
            val currentVal = prefs.getInt(prefKey, defaultValue)
            progress = currentVal
            textView.text = "$currentVal$suffix"
            
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    textView.text = "$progress$suffix"
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
