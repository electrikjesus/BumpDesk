package com.bass.bumpdesk

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {
    private enum class DesktopMode {
        ROOM,
        INFINITE,
        FLAT_FLOOR,
    }

    private data class DesktopOption(
        val mode: DesktopMode,
        val previewRes: Int,
        val titleRes: Int,
        val subtitleRes: Int,
    )

    private lateinit var appManager: AppManager
    private var pendingWallpaperSwitch: MaterialSwitch? = null
    private lateinit var wallpaperFloorSwitch: MaterialSwitch
    private lateinit var expressiveFolderChromeGroup: View
    private lateinit var expressiveM3FolderChromeSwitch: MaterialSwitch
    private val desktopCards = linkedMapOf<DesktopMode, MaterialCardView>()
    private var selectedDesktopMode = DesktopMode.ROOM

    private val desktopOptions = listOf(
        DesktopOption(
            DesktopMode.ROOM,
            R.drawable.settings_preview_room,
            R.string.settings_desktop_room_title,
            R.string.settings_desktop_room_subtitle,
        ),
        DesktopOption(
            DesktopMode.INFINITE,
            R.drawable.settings_preview_infinite,
            R.string.settings_desktop_infinite_title,
            R.string.settings_desktop_infinite_subtitle,
        ),
        DesktopOption(
            DesktopMode.FLAT_FLOOR,
            R.drawable.settings_preview_flat_floor,
            R.string.settings_desktop_flat_floor_title,
            R.string.settings_desktop_flat_floor_subtitle,
        ),
    )

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val switch = pendingWallpaperSwitch ?: return@registerForActivityResult
        if (granted) {
            enableWallpaperFloorPref(switch, allowFallbackDialog = true)
        } else {
            showWallpaperFallbackDialog(switch)
        }
    }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val switch = pendingWallpaperSwitch ?: return@registerForActivityResult
        WallpaperPermissions.logStatus(this, "mediaPermissionResult granted=$granted")
        if (granted) {
            enableWallpaperFloorPref(switch, allowFallbackDialog = true)
        } else {
            showPhotosPermissionRequiredDialog(switch)
        }
    }

    private var awaitingWallpaperPermissionFromSettings = false

    private val pickWallpaperLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val switch = pendingWallpaperSwitch
        pendingWallpaperSwitch = null
        if (uri == null) {
            resetWallpaperSwitch(switch ?: wallpaperFloorSwitch)
            return@registerForActivityResult
        }
        val (cropW, cropH) = FlatFloorMode.floorCropAspectFor(this)
        if (WallpaperFloorProvider.savePickedWallpaper(this, uri, cropW, cropH)) {
            getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("use_wallpaper_as_floor", true).apply()
            switch?.isChecked = true
            Toast.makeText(this, "Wallpaper floor enabled.", Toast.LENGTH_SHORT).show()
        } else {
            resetWallpaperSwitch(switch ?: wallpaperFloorSwitch)
            Toast.makeText(this, "Could not load the selected image.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)
        setupWindowInsets()

        appManager = AppManager(this)
        val prefs = getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        ThemeManager.init(this)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        wallpaperFloorSwitch = findViewById(R.id.switchUseWallpaperAsFloor)
        expressiveFolderChromeGroup = findViewById(R.id.groupExpressiveFolderChrome)
        expressiveM3FolderChromeSwitch = findViewById(R.id.switchExpressiveM3FolderChrome)

        setupDesktopModeCards(prefs)
        setupAppToggles(prefs)
        setupLaunchPreferences(prefs)
        setupRecentsViewMode(prefs)
        setupThemePicker(prefs)
        setupExpressiveFolderChrome(prefs)
        setupRadialMenuSize(prefs)
        setupSliders()
        setupMaintenanceActions(prefs)

        findViewById<ExtendedFloatingActionButton>(R.id.btnApplyChanges).setOnClickListener {
            finish()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBar)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsScroll)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.btnApplyChanges)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = view.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            lp.bottomMargin = 20 + systemBars.bottom
            view.layoutParams = lp
            insets
        }
    }

    private fun setupDesktopModeCards(prefs: android.content.SharedPreferences) {
        val container = findViewById<android.widget.LinearLayout>(R.id.desktopOptionsContainer)
        val inflater = LayoutInflater.from(this)
        desktopOptions.forEach { option ->
            val card = inflater.inflate(R.layout.item_settings_desktop_option, container, false) as MaterialCardView
            card.findViewById<android.widget.ImageView>(R.id.ivPreview).setImageResource(option.previewRes)
            card.findViewById<TextView>(R.id.tvTitle).setText(option.titleRes)
            card.findViewById<TextView>(R.id.tvSubtitle).setText(option.subtitleRes)
            card.setOnClickListener { selectDesktopMode(option.mode, persist = true) }
            container.addView(card)
            desktopCards[option.mode] = card
        }
        selectedDesktopMode = readDesktopMode(prefs)
        applyDesktopOptionsLayout()
        refreshDesktopModeCards()
    }

    private fun applyDesktopOptionsLayout() {
        val container = findViewById<android.widget.LinearLayout>(R.id.desktopOptionsContainer)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        container.orientation = if (landscape) {
            android.widget.LinearLayout.HORIZONTAL
        } else {
            android.widget.LinearLayout.VERTICAL
        }
        val gap = resources.getDimensionPixelSize(R.dimen.settings_desktop_card_gap)
        desktopCards.values.forEachIndexed { index, card ->
            val lp = android.widget.LinearLayout.LayoutParams(
                if (landscape) 0 else android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                if (landscape) 1f else 0f,
            )
            if (landscape) {
                lp.marginStart = if (index == 0) 0 else gap
            } else {
                lp.topMargin = if (index == 0) 0 else gap
            }
            card.layoutParams = lp
        }
    }

    private fun readDesktopMode(prefs: android.content.SharedPreferences): DesktopMode = when {
        prefs.getBoolean("infinite_desktop_mode", false) -> DesktopMode.INFINITE
        prefs.getBoolean(FlatFloorMode.PREF_KEY, false) -> DesktopMode.FLAT_FLOOR
        else -> DesktopMode.ROOM
    }

    private fun selectDesktopMode(mode: DesktopMode, persist: Boolean) {
        selectedDesktopMode = mode
        if (persist) {
            getSharedPreferences("bump_prefs", Context.MODE_PRIVATE).edit().apply {
                putBoolean("infinite_desktop_mode", mode == DesktopMode.INFINITE)
                putBoolean(FlatFloorMode.PREF_KEY, mode == DesktopMode.FLAT_FLOOR)
                apply()
            }
        }
        refreshDesktopModeCards()
    }

    private fun refreshDesktopModeCards() {
        val strokeSelected = resources.getDimensionPixelSize(R.dimen.settings_desktop_card_stroke_selected)
        val strokeDefault = resources.getDimensionPixelSize(R.dimen.settings_desktop_card_stroke_default)
        desktopCards.forEach { (mode, card) ->
            val selected = mode == selectedDesktopMode
            card.strokeWidth = if (selected) strokeSelected else strokeDefault
            card.strokeColor = getColor(
                if (selected) R.color.settings_card_selected_stroke else R.color.m3_outline_variant
            )
            card.setCardBackgroundColor(
                getColor(if (selected) R.color.m3_surface_container_highest else R.color.m3_surface_container)
            )
            card.findViewById<View>(R.id.ivSelected).visibility =
                if (selected) View.VISIBLE else View.GONE
        }
    }

    private fun setupAppToggles(prefs: android.content.SharedPreferences) {
        findViewById<MaterialSwitch>(R.id.switchShowRecentApps).apply {
            isChecked = prefs.getBoolean("show_recent_apps", true)
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !appManager.hasUsageStatsPermission()) {
                    showUsageAccessDialog()
                }
                prefs.edit().putBoolean("show_recent_apps", isChecked).apply()
            }
        }

        findViewById<MaterialSwitch>(R.id.switchShowAppDrawerIcon).apply {
            isChecked = prefs.getBoolean("show_app_drawer_icon", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("show_app_drawer_icon", isChecked).apply()
            }
        }

        wallpaperFloorSwitch.apply {
            isChecked = prefs.getBoolean("use_wallpaper_as_floor", false)
            setOnCheckedChangeListener { button, isChecked ->
                if (!isChecked) {
                    prefs.edit().putBoolean("use_wallpaper_as_floor", false).apply()
                    WallpaperFloorProvider.clear()
                    WallpaperFloorProvider.clearPickedWallpaper(this@SettingsActivity)
                    return@setOnCheckedChangeListener
                }
                beginWallpaperSetup(button as MaterialSwitch)
            }
        }
        updateRecentsSnapshotStatus()
    }

    private fun setupLaunchPreferences(prefs: android.content.SharedPreferences) {
        findViewById<MaterialSwitch>(R.id.switchRememberLaunchMode).apply {
            isChecked = LaunchPreferences.rememberLaunchMode(prefs)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(LaunchPreferences.PREF_REMEMBER_LAUNCH_MODE, isChecked).apply()
            }
        }

        val group = findViewById<RadioGroup>(R.id.rgDefaultLaunchMode)
        when (LaunchPreferences.defaultLaunchMode(prefs)) {
            "maximized" -> group.check(R.id.rbLaunchMaximized)
            "portrait" -> group.check(R.id.rbLaunchPortrait)
            "fullscreen" -> group.check(R.id.rbLaunchFullscreen)
            else -> group.check(R.id.rbLaunchStandard)
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbLaunchMaximized -> "maximized"
                R.id.rbLaunchPortrait -> "portrait"
                R.id.rbLaunchFullscreen -> "fullscreen"
                else -> "standard"
            }
            prefs.edit().putString(LaunchPreferences.PREF_DEFAULT_LAUNCH_MODE, mode).apply()
        }

        val scale = LaunchPreferences.scaleFactor(prefs)
        val slider = findViewById<Slider>(R.id.sliderLaunchScale)
        val scaleLabel = findViewById<TextView>(R.id.tvLaunchScaleVal)
        slider.value = (scale * 100f).coerceIn(50f, 150f)
        scaleLabel.text = String.format("%.0f%%", slider.value)
        slider.addOnChangeListener { _, value, _ ->
            prefs.edit().putFloat(LaunchPreferences.PREF_SCALE_FACTOR, value / 100f).apply()
            scaleLabel.text = String.format("%.0f%%", value)
        }
    }

    private fun setupRecentsViewMode(prefs: android.content.SharedPreferences) {
        val group = findViewById<RadioGroup>(R.id.rgRecentsViewMode)
        val iconsMode = prefs.getString(RecentsPreferences.PREF_VIEW_MODE, RecentsPreferences.VIEW_ICONS) !=
            RecentsPreferences.VIEW_TASK_CARDS
        group.check(if (iconsMode) R.id.rbRecentsIcons else R.id.rbRecentsTaskCards)
        group.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rbRecentsTaskCards) {
                RecentsPreferences.VIEW_TASK_CARDS
            } else {
                RecentsPreferences.VIEW_ICONS
            }
            if (prefs.getString(RecentsPreferences.PREF_VIEW_MODE, RecentsPreferences.VIEW_ICONS) != mode) {
                prefs.edit().putString(RecentsPreferences.PREF_VIEW_MODE, mode).apply()
            }
        }
    }

    private fun setupRadialMenuSize(prefs: android.content.SharedPreferences) {
        val group = findViewById<RadioGroup>(R.id.rgRadialMenuSize)
        val checkedId = when (RadialMenuPreferences.readPreset(prefs)) {
            RadialMenuPreferences.PRESET_PHONE -> R.id.rbRadialMenuPhone
            RadialMenuPreferences.PRESET_TABLET -> R.id.rbRadialMenuTablet
            RadialMenuPreferences.PRESET_LARGE -> R.id.rbRadialMenuLarge
            else -> R.id.rbRadialMenuAuto
        }
        group.check(checkedId)
        group.setOnCheckedChangeListener { _, id ->
            val preset = when (id) {
                R.id.rbRadialMenuPhone -> RadialMenuPreferences.PRESET_PHONE
                R.id.rbRadialMenuTablet -> RadialMenuPreferences.PRESET_TABLET
                R.id.rbRadialMenuLarge -> RadialMenuPreferences.PRESET_LARGE
                else -> RadialMenuPreferences.PRESET_AUTO
            }
            if (RadialMenuPreferences.readPreset(prefs) != preset) {
                prefs.edit().putString(RadialMenuPreferences.PREF_KEY, preset).apply()
            }
        }
    }

    private fun setupThemePicker(prefs: android.content.SharedPreferences) {
        val themes = ThemeManager.getThemeList(this)
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnChangeTheme).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_select_theme))
                .setItems(themes.toTypedArray()) { _, which ->
                    val selected = themes[which]
                    prefs.edit().putString("selected_theme", selected).apply()
                    ThemeManager.setTheme(selected, this@SettingsActivity)
                    updateExpressiveFolderChromeVisibility()
                    Toast.makeText(this@SettingsActivity, "Theme set to: $selected", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
        updateExpressiveFolderChromeVisibility()
    }

    private fun setupExpressiveFolderChrome(prefs: android.content.SharedPreferences) {
        expressiveM3FolderChromeSwitch.apply {
            isChecked = prefs.getBoolean(ThemeManager.PREF_EXPRESSIVE_M3_FOLDER_CHROME, true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(ThemeManager.PREF_EXPRESSIVE_M3_FOLDER_CHROME, isChecked).apply()
            }
        }
    }

    private fun updateExpressiveFolderChromeVisibility() {
        val visible = ThemeManager.usesSystemColors()
        expressiveFolderChromeGroup.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun setupSliders() {
        setupSlider(R.id.sliderFriction, R.id.tvFrictionVal, "physics_friction", 94, "%")
        setupSlider(R.id.sliderBounciness, R.id.tvBouncinessVal, "physics_bounciness", 25, "%")
        setupSlider(R.id.sliderGravity, R.id.tvGravityVal, "physics_gravity", 10, "%")
        setupSlider(R.id.sliderItemScale, R.id.tvItemScaleVal, "layout_item_scale", 50, "%")
        setupSlider(R.id.sliderGridSpacing, R.id.tvGridSpacingVal, "layout_grid_spacing", 60, "%")
        setupSlider(R.id.sliderRoomSize, R.id.tvRoomSizeVal, "room_size_scale", 30, "")
    }

    private fun setupMaintenanceActions(prefs: android.content.SharedPreferences) {
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnResetCamera).setOnClickListener {
            prefs.edit().apply {
                remove("cam_def_pos_x"); remove("cam_def_pos_y"); remove("cam_def_pos_z")
                remove("cam_def_lat_x"); remove("cam_def_lat_y"); remove("cam_def_lat_z")
                putBoolean("reset_camera_trigger", true)
                apply()
            }
            Toast.makeText(this, "Camera defaults restored.", Toast.LENGTH_SHORT).show()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnResetDesktopLayout).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_reset_desktop_layout)
                .setMessage(R.string.settings_reset_desktop_layout_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_reset_desktop_layout) { _, _ ->
                    prefs.edit().putBoolean("reset_desktop_trigger", true).apply()
                    Toast.makeText(this, R.string.settings_reset_desktop_layout_done, Toast.LENGTH_SHORT).show()
                    finish()
                }
                .show()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClearCache).setOnClickListener {
            prefs.edit().apply {
                remove("onboarding_complete")
                putString("selected_theme", ThemeManager.DEFAULT_THEME)
                apply()
            }
            ThemeManager.setTheme(ThemeManager.DEFAULT_THEME, this)
            Toast.makeText(this, "Cache cleared. Some changes require restart.", Toast.LENGTH_SHORT).show()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnResetDefaults).setOnClickListener {
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
                putBoolean(FlatFloorMode.PREF_KEY, false)
                putBoolean("use_wallpaper_as_floor", true)
                putString("selected_theme", ThemeManager.DEFAULT_THEME)
                putBoolean(ThemeManager.PREF_EXPRESSIVE_M3_FOLDER_CHROME, true)
                putString(RadialMenuPreferences.PREF_KEY, RadialMenuPreferences.PRESET_AUTO)
                putString(RecentsPreferences.PREF_VIEW_MODE, RecentsPreferences.VIEW_ICONS)
                apply()
            }
            ThemeManager.setTheme(ThemeManager.DEFAULT_THEME, this)
            WallpaperFloorProvider.clearPickedWallpaper(this)
            recreate()
        }
    }

    private fun setupSlider(sliderId: Int, valueId: Int, prefKey: String, defaultValue: Int, suffix: String) {
        val prefs = getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        val valueView = findViewById<TextView>(valueId)
        findViewById<Slider>(sliderId).apply {
            val currentVal = prefs.getInt(prefKey, defaultValue).toFloat()
            value = currentVal
            valueView.text = "${currentVal.toInt()}$suffix"
            addOnChangeListener { _, value, fromUser ->
                valueView.text = "${value.toInt()}$suffix"
                if (fromUser) prefs.edit().putInt(prefKey, value.toInt()).apply()
            }
        }
    }

    private fun beginWallpaperSetup(switch: MaterialSwitch) {
        pendingWallpaperSwitch = switch
        WallpaperPermissions.logStatus(this, "beginWallpaperSetup")
        enableWallpaperFloorPref(switch, allowFallbackDialog = false)
    }

    private fun requestStorageForWallpaper(switch: MaterialSwitch) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestMediaImagesForWallpaper(switch)
            return
        }
        val launchRequest = {
            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
        ) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Storage Permission Required")
                .setMessage(
                    "The system wallpaper API requires Storage permission. " +
                        "You can also pick the wallpaper image manually."
                )
                .setPositiveButton("Allow Storage") { _, _ -> launchRequest() }
                .setNeutralButton("Pick Image") { _, _ -> showWallpaperFallbackDialog(switch) }
                .setNegativeButton("Cancel") { _, _ ->
                    pendingWallpaperSwitch = null
                }
                .show()
        } else {
            launchRequest()
        }
    }

    private fun requestMediaImagesForWallpaper(switch: MaterialSwitch) {
        pendingWallpaperSwitch = switch
        switch.isChecked = false
        if (WallpaperPermissions.hasRuntimePermission(this)) {
            showLiveWallpaperAccessDialog(switch)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES)
        ) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Photos Permission Required")
                .setMessage(
                    "Allow Photos and videos access so BumpDesk can read the system wallpaper " +
                        "for the floor. You can also pick the image manually."
                )
                .setPositiveButton("Allow") { _, _ ->
                    mediaPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
                .setNeutralButton("Pick Image") { _, _ -> showWallpaperFallbackDialog(switch) }
                .setNegativeButton("Cancel") { _, _ ->
                    pendingWallpaperSwitch = null
                }
                .show()
        } else {
            mediaPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        }
    }

    private fun showPhotosPermissionRequiredDialog(switch: MaterialSwitch) {
        if (isFinishing || isDestroyed) return
        pendingWallpaperSwitch = switch
        WallpaperPermissions.logStatus(this, "showPhotosPermissionRequiredDialog")
        MaterialAlertDialogBuilder(this)
            .setTitle("Photos Permission Required")
            .setMessage(
                "Photos and videos access was denied. Allow it in permission settings, " +
                    "or pick the wallpaper image manually."
            )
            .setPositiveButton("Permission Settings") { _, _ ->
                awaitingWallpaperPermissionFromSettings = true
                WallpaperPermissions.openPhotosPermissionSettings(this)
            }
            .setNeutralButton("Pick Image") { _, _ ->
                awaitingWallpaperPermissionFromSettings = false
                showWallpaperFallbackDialog(switch)
            }
            .setNegativeButton("Cancel") { _, _ ->
                awaitingWallpaperPermissionFromSettings = false
                pendingWallpaperSwitch = null
            }
            .show()
    }

    private fun showLiveWallpaperAccessDialog(switch: MaterialSwitch) {
        if (isFinishing || isDestroyed) return
        pendingWallpaperSwitch = switch
        val status = WallpaperPermissions.diagnose(this)
        WallpaperPermissions.logStatus(this, "showLiveWallpaperAccessDialog")
        val reason = status.blockingReason().orEmpty()
        MaterialAlertDialogBuilder(this)
            .setTitle("System Wallpaper Access")
            .setMessage(
                buildString {
                    if (reason.isNotEmpty()) {
                        append(reason)
                        append("\n\n")
                    }
                    append("Choose one:\n")
                    append("• Pick Image — select the same wallpaper from Photos\n")
                    append("• adb — for live system wallpaper on Waydroid:\n")
                    append("adb shell pm grant com.bass.bumpdesk android.permission.READ_EXTERNAL_STORAGE")
                }
            )
            .setPositiveButton("Pick Image") { _, _ ->
                launchWallpaperPicker()
            }
            .setNegativeButton("Cancel") { _, _ ->
                awaitingWallpaperPermissionFromSettings = false
                pendingWallpaperSwitch = null
                resetWallpaperSwitch(switch)
            }
            .show()
    }

    private fun launchWallpaperPicker() {
        pickWallpaperLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun enableWallpaperFloorPref(switch: MaterialSwitch, allowFallbackDialog: Boolean) {
        val (cropW, cropH) = FlatFloorMode.floorCropAspectFor(this)
        try {
            WallpaperFloorProvider.refreshWithRetry(this, cropW, cropH) { loaded ->
                if (isFinishing || isDestroyed) return@refreshWithRetry
                handleWallpaperFloorLoadResult(switch, allowFallbackDialog, loaded)
            }
        } catch (e: Exception) {
            BumpDeskLog.fail(
                BumpDeskLog.Tag.WALLPAPER,
                "enableWallpaperFloorPref",
                WallpaperPermissions.diagnose(this).toLogString(),
                e
            )
            resetWallpaperSwitch(switch)
            Toast.makeText(this, "Could not load wallpaper: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleWallpaperFloorLoadResult(
        switch: MaterialSwitch,
        allowFallbackDialog: Boolean,
        loaded: Boolean,
    ) {
        if (loaded) {
            pendingWallpaperSwitch = null
            getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("use_wallpaper_as_floor", true).apply()
            switch.isChecked = true
            Toast.makeText(this, "Wallpaper floor enabled.", Toast.LENGTH_SHORT).show()
            return
        }
        WallpaperPermissions.logStatus(this, "enableWallpaperFloorPref_failed")
        if (!allowFallbackDialog) {
            val status = WallpaperPermissions.diagnose(this@SettingsActivity)
            pendingWallpaperSwitch = switch
            switch.isChecked = false
            when {
                status.needsMediaImagesPrompt() -> requestMediaImagesForWallpaper(switch)
                WallpaperPermissions.shouldPromptLegacyStorage(this@SettingsActivity) ->
                    showLiveWallpaperAccessDialog(switch)
                else -> requestStorageForWallpaper(switch)
            }
            return
        }
        if (WallpaperPermissions.shouldPromptLegacyStorage(this)) {
            pendingWallpaperSwitch = switch
            switch.isChecked = false
            showLiveWallpaperAccessDialog(switch)
            return
        }
        pendingWallpaperSwitch = null
        resetWallpaperSwitch(switch)
        showWallpaperFallbackDialog(switch)
    }

    private fun resetWallpaperSwitch(switch: MaterialSwitch) {
        switch.isChecked = false
        getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("use_wallpaper_as_floor", false).apply()
    }

    private fun showWallpaperFallbackDialog(switch: MaterialSwitch) {
        if (isFinishing || isDestroyed) return
        pendingWallpaperSwitch = switch
        WallpaperPermissions.logStatus(this, "showWallpaperFallbackDialog")
        MaterialAlertDialogBuilder(this)
            .setTitle("Use Wallpaper Image")
            .setMessage(
                "BumpDesk could not read the live system wallpaper on this device. " +
                    "Pick the same image from Photos to use it on the floor."
            )
            .setPositiveButton("Pick Image") { _, _ ->
                launchWallpaperPicker()
            }
            .setNegativeButton("Cancel") { _, _ ->
                pendingWallpaperSwitch = null
                resetWallpaperSwitch(switch)
            }
            .show()
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
                findViewById<MaterialSwitch>(R.id.switchShowRecentApps).isChecked = false
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateRecentsSnapshotStatus()
        if (!awaitingWallpaperPermissionFromSettings) return
        val switch = pendingWallpaperSwitch ?: return
        awaitingWallpaperPermissionFromSettings = false
        enableWallpaperFloorPref(switch, allowFallbackDialog = true)
    }

    private fun updateRecentsSnapshotStatus() {
        findViewById<TextView>(R.id.tvRecentsSnapshotStatus)?.text =
            RecentsSnapshotCapability.settingsLabel(this)
    }
}
