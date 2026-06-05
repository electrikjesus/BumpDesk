package com.bass.bumpdesk

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.SharedPreferences
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import kotlin.math.hypot

class LauncherActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: BumpRenderer
    private lateinit var gestureDetector: GestureDetector
    
    private lateinit var btnResetView: Button
    private lateinit var radialMenu: RadialMenuView
    private lateinit var widgetContainer: FrameLayout
    
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: BumpAppWidgetHost
    
    private lateinit var appManager: AppManager
    private lateinit var menuManager: MenuManager
    
    private lateinit var dialogManager: DialogManager
    private lateinit var actionHandler: ActionHandler

    private var isTwoFingerActive = false
    private var lastTwoFingerSpan = 0f
    private var twoFingerStartSpan = 0f
    private var twoFingerPanAccum = 0f
    private var twoFingerZoomFactorAccum = 1f
    private var lastGestureDebugLogMs = 0L
    private var lastMultiTouchPointerCount = 0
    private var maxGesturePointerCount = 0
    private var isMiddleDragging = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f
    private var selectedItemForPhoto: BumpItem? = null
    
    private var touchSlop = 25f

    private val recentsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                val reason = intent.getStringExtra("reason")
                if (reason == "recentapps") {
                    updateRecents()
                }
            }
        }
    }

    companion object {
        const val APPWIDGET_HOST_ID = 1024
        const val REQUEST_PICK_APPWIDGET = 1
        const val REQUEST_CREATE_APPWIDGET = 2
        const val REQUEST_PICK_IMAGE = 3
        const val REQUEST_PICK_IMAGE_FOR_FRAME = 4
        
        const val WINDOWING_MODE_UNDEFINED = 0
        const val WINDOWING_MODE_FULLSCREEN = 1
        const val WINDOWING_MODE_PINNED = 2
        const val WINDOWING_MODE_FREEFORM = 5
        
        const val ACTION_RECENTS = "com.bass.bumpdesk.ACTION_RECENTS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ThemeManager.init(this)
        val prefs = getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        ScreenMetrics.applyFirstLaunchDefaults(this, prefs)
        touchSlop = ScreenMetrics.touchSlopPx(this)
        prefs.registerOnSharedPreferenceChangeListener(this)

        if (!prefs.getBoolean("onboarding_complete", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        setupSystemUi()

        setContentView(R.layout.activity_launcher)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appManager = AppManager(this)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.preserveEGLContextOnPause = true

        renderer = BumpRenderer(this)
        renderer.glSurfaceView = glSurfaceView
        glSurfaceView.setRenderer(renderer)

        appWidgetHost = BumpAppWidgetHost(this, APPWIDGET_HOST_ID) { appWidgetId ->
            if (!::renderer.isInitialized || !::glSurfaceView.isInitialized) return@BumpAppWidgetHost
            glSurfaceView.queueEvent {
                renderer.sceneState.widgetItems.find { it.appWidgetId == appWidgetId }?.let { widget ->
                    widget.textureId = -1
                }
                glSurfaceView.requestRender()
            }
        }
        CameraDiagnostics.logProbe(this, "launcherReady")
        CameraDiagnostics.log(renderer.camera, "launcherReady", "afterRendererInit")

        dialogManager = DialogManager(this, glSurfaceView, renderer)
        actionHandler = ActionHandler(this, glSurfaceView, renderer)

        btnResetView = findViewById(R.id.btnResetView)
        radialMenu = findViewById(R.id.radialMenu)
        widgetContainer = findViewById(R.id.widgetContainer)
        menuManager = MenuManager(this, radialMenu, glSurfaceView, renderer, this)
        
        btnResetView.setOnClickListener {
            if (::renderer.isInitialized) {
                CameraDiagnostics.log(renderer.camera, "resetViewRequested", "uiThread")
                glSurfaceView.queueEvent {
                    renderer.resetView()
                    CameraDiagnostics.log(renderer.camera, "resetViewComplete", "glThread")
                }
            }
        }

        setupGestures()
        loadApps()
        actionHandler.handleIntent(intent) { showResetButton(it) }

        appManager.setUpdateListener(object : AppManager.RecentsUpdateListener {
            override fun onRecentsUpdated(recents: List<AppInfo>) {
                if (::renderer.isInitialized) {
                    glSurfaceView.queueEvent { renderer.updateRecents(recents) }
                }
            }
        })
    }

    private var pendingThemeReload = false
    private var pendingFloorReload = false
    private var pendingSettingsUpdate = false

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (!::renderer.isInitialized) return

        when (key) {
            "use_wallpaper_as_floor" -> {
                BumpDeskLog.enter(BumpDeskLog.Tag.CORE, "onSharedPreferenceChanged", "key=$key")
                pendingFloorReload = true
                BumpDeskLog.exit(BumpDeskLog.Tag.CORE, "onSharedPreferenceChanged", "key=$key deferred")
            }
            "selected_theme" -> {
                BumpDeskLog.enter(BumpDeskLog.Tag.CORE, "onSharedPreferenceChanged", "key=$key")
                ThemeManager.init(this, forceReload = true)
                pendingThemeReload = true
                BumpDeskLog.exit(BumpDeskLog.Tag.CORE, "onSharedPreferenceChanged", "key=$key deferred")
            }
            "infinite_desktop_mode", FlatFloorMode.PREF_KEY -> {
                BumpDeskLog.enter(BumpDeskLog.Tag.CORE, "onSharedPreferenceChanged", "key=$key")
                pendingSettingsUpdate = true
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    applyPendingPreferenceUpdates(sharedPreferences)
                }
                BumpDeskLog.exit(BumpDeskLog.Tag.CORE, "onSharedPreferenceChanged", "key=$key deferred")
            }
            "show_recent_apps" -> {
                pendingSettingsUpdate = true
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    applyPendingPreferenceUpdates(sharedPreferences)
                }
            }
            RecentsPreferences.PREF_VIEW_MODE, RecentsPreferences.PREF_PINNED_OPEN -> {
                pendingSettingsUpdate = true
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    applyPendingPreferenceUpdates(sharedPreferences)
                }
            }
            else -> {
                if (key == "room_size_scale" ||
                    key?.startsWith("physics_") == true ||
                    key?.startsWith("layout_") == true
                ) {
                    pendingSettingsUpdate = true
                    if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                        applyPendingPreferenceUpdates(sharedPreferences)
                    }
                }
            }
        }
    }

    private var didReloadFloorThisResume = false

    private fun applyPendingPreferenceUpdates(sharedPreferences: SharedPreferences?) {
        if (!::renderer.isInitialized) return
        didReloadFloorThisResume = false
        if (pendingThemeReload) {
            prepareWallpaperFloorIfNeeded(sharedPreferences) {
                renderer.reloadTheme()
                pendingThemeReload = false
                finishPendingPreferenceUpdates(sharedPreferences)
            }
        } else if (pendingFloorReload) {
            didReloadFloorThisResume = true
            prepareWallpaperFloorIfNeeded(sharedPreferences) {
                renderer.reloadFloorTexture()
                pendingFloorReload = false
                finishPendingPreferenceUpdates(sharedPreferences)
            }
        } else {
            finishPendingPreferenceUpdates(sharedPreferences)
        }
    }

    private fun finishPendingPreferenceUpdates(sharedPreferences: SharedPreferences?) {
        if (pendingSettingsUpdate) {
            renderer.updateSettings()
            pendingSettingsUpdate = false
        }
        if (sharedPreferences?.getBoolean("show_recent_apps", true) == true) {
            updateRecents()
        }
    }

    private fun prepareWallpaperFloorIfNeeded(
        sharedPreferences: SharedPreferences?,
        onReady: (() -> Unit)? = null
    ) {
        val prefs = sharedPreferences ?: getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("use_wallpaper_as_floor", false)) {
            WallpaperFloorProvider.clear()
            onReady?.invoke()
            return
        }
        val (cropW, cropH) = FlatFloorMode.floorCropAspectFor(this)
        if (WallpaperFloorProvider.hasBitmap()) {
            WallpaperFloorProvider.updateFloorCropAspect(cropW, cropH)
            onReady?.invoke()
            return
        }
        WallpaperFloorProvider.refreshWithRetry(this, cropW, cropH) { loaded ->
            if (!loaded) {
                BumpDeskLog.w(
                    BumpDeskLog.Tag.WALLPAPER,
                    "prepareWallpaperFloorIfNeeded",
                    "could not load system wallpaper for floor"
                )
            }
            onReady?.invoke()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val prefs = getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        
        if (::renderer.isInitialized) {
            renderer.onDestroy()
        }
        
        if (::appWidgetHost.isInitialized) {
            try { appWidgetHost.stopListening() } catch (e: Exception) {}
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { actionHandler.handleIntent(it) { show -> showResetButton(show) } }
    }

    private fun setupSystemUi() {
        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            navigationBarColor = Color.TRANSPARENT
            statusBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
                isStatusBarContrastEnforced = false
            }
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (radialMenu.visibility == View.VISIBLE || !::renderer.isInitialized) return true
                glSurfaceView.queueEvent { renderer.handleDoubleTap(e.x, e.y) }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (radialMenu.visibility == View.VISIBLE || !::renderer.isInitialized) return true
                glSurfaceView.queueEvent { renderer.handleSingleTap(e.x, e.y) }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (radialMenu.visibility == View.VISIBLE || !::renderer.isInitialized) return
                glSurfaceView.queueEvent { renderer.handleLongPress(e.x, e.y) }
            }
            
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (radialMenu.visibility == View.VISIBLE || isTwoFingerActive || isMiddleDragging) return true
                return false
            }
        })

        glSurfaceView.setOnTouchListener { _, event ->
            if (!::renderer.isInitialized) return@setOnTouchListener false
            
            if (radialMenu.visibility == View.VISIBLE) {
                return@setOnTouchListener radialMenu.dispatchTouchEvent(event)
            }
            
            val pointerCount = event.pointerCount
            val isRightButton = (event.buttonState and MotionEvent.BUTTON_SECONDARY) != 0
            val isMiddleButton = (event.buttonState and MotionEvent.BUTTON_TERTIARY) != 0
            
            if (!isTwoFingerActive && !isMiddleDragging && !isRightButton) {
                gestureDetector.onTouchEvent(event)
            }
            
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (pointerCount >= 2) {
                        beginTwoFingerGesture(event)
                        glSurfaceView.queueEvent { renderer.interactionManager.cancelPendingInteractions() }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pointerCount >= 2 && !isMiddleDragging) {
                        handleTwoFingerMove(event)
                        return@setOnTouchListener true
                    } else if (isMiddleDragging) {
                        val currentX = event.x
                        val currentY = event.y
                        val dx = currentX - lastMidX
                        val dy = currentY - lastMidY
                        if (!isMiddleButton) {
                            isMiddleDragging = false
                        } else {
                            glSurfaceView.queueEvent { renderer.handlePan(dx, dy) }
                        }
                        lastMidX = currentX
                        lastMidY = currentY
                        return@setOnTouchListener true
                    } else if (pointerCount == 1 && !isTwoFingerActive && !isRightButton) {
                        val dist = hypot(event.x - initialTouchX, event.y - initialTouchY)
                        if (dist > touchSlop) {
                            glSurfaceView.queueEvent { renderer.handleTouchMove(event.x, event.y, pointerCount) }
                        }
                    }
                }
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.x
                    initialTouchY = event.y
                    if (isMiddleButton) {
                        isMiddleDragging = true
                        lastMidX = event.x
                        lastMidY = event.y
                        return@setOnTouchListener true
                    } else if (isRightButton) {
                        glSurfaceView.queueEvent { renderer.handleLongPress(event.x, event.y) }
                        return@setOnTouchListener true
                    }
                    glSurfaceView.queueEvent { renderer.handleTouchDown(event.x, event.y) }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount < 2) {
                        endTwoFingerGesture(event)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    endTwoFingerGesture(event)
                    if (isMiddleDragging) {
                        isMiddleDragging = false
                        return@setOnTouchListener true
                    }
                    glSurfaceView.queueEvent { renderer.handleTouchUp() }
                }
            }

            true
        }
    }

    private fun twoFingerSpan(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
    }

    private fun twoFingerMidpoint(event: MotionEvent): Pair<Float, Float> {
        val x = (0 until event.pointerCount).map { event.getX(it) }.average().toFloat()
        val y = (0 until event.pointerCount).map { event.getY(it) }.average().toFloat()
        return x to y
    }

    private fun beginTwoFingerGesture(event: MotionEvent) {
        val (midX, midY) = twoFingerMidpoint(event)
        lastMidX = midX
        lastMidY = midY
        lastTwoFingerSpan = twoFingerSpan(event)
        twoFingerStartSpan = lastTwoFingerSpan
        twoFingerPanAccum = 0f
        twoFingerZoomFactorAccum = 1f
        isTwoFingerActive = true
        lastMultiTouchPointerCount = 0
        maxGesturePointerCount = event.pointerCount
        BumpDeskLog.i(
            BumpDeskLog.Tag.GESTURE,
            "twoFingerStart",
            "span=${"%.1f".format(lastTwoFingerSpan)} slop=${"%.1f".format(touchSlop)} pointers=${event.pointerCount}"
        )
    }

    private fun handleTwoFingerMove(event: MotionEvent) {
        val pointerCount = event.pointerCount
        val (currentX, currentY) = twoFingerMidpoint(event)
        val span = twoFingerSpan(event)
        val midDx = currentX - lastMidX
        val midDy = currentY - lastMidY
        val panDistance = hypot(midDx, midDy)
        twoFingerPanAccum += panDistance

        if (lastTwoFingerSpan > 0f && pointerCount == 2) {
            val factor = span / lastTwoFingerSpan
            if (abs(factor - 1f) > 0.001f) {
                twoFingerZoomFactorAccum *= factor
                glSurfaceView.queueEvent { renderer.handleZoom(factor) }
            }
            glSurfaceView.queueEvent { renderer.handlePan(midDx, midDy) }
        } else if (pointerCount == 3) {
            // Skip the first move after a finger count change (midpoint jumps when a finger is added).
            if (lastMultiTouchPointerCount == 3) {
                glSurfaceView.queueEvent { renderer.handleOrbit(midDx, midDy) }
            }
        }

        maybeLogMultiTouchDebug(pointerCount, span, panDistance, midDx, midDy)

        lastMultiTouchPointerCount = pointerCount
        maxGesturePointerCount = maxOf(maxGesturePointerCount, pointerCount)
        lastTwoFingerSpan = span
        lastMidX = currentX
        lastMidY = currentY
    }

    private fun maybeLogMultiTouchDebug(
        pointerCount: Int,
        span: Float,
        panDistance: Float,
        midDx: Float,
        midDy: Float,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastGestureDebugLogMs < 120L) return
        lastGestureDebugLogMs = now
        val spanDelta = abs(span - lastTwoFingerSpan)
        val tag = if (pointerCount >= 3) "threeFingerMove" else "twoFingerMove"
        BumpDeskLog.d(
            BumpDeskLog.Tag.GESTURE,
            tag,
            "pointers=$pointerCount spanΔ=${"%.1f".format(spanDelta)} pan=${"%.1f".format(panDistance)} " +
                "dx=${"%.1f".format(midDx)} dy=${"%.1f".format(midDy)} zoom=${"%.3f".format(renderer.camera.zoomLevel)}"
        )
    }

    private fun endTwoFingerGesture(event: MotionEvent) {
        if (!isTwoFingerActive) return
        val endSpan = twoFingerSpan(event).takeIf { it > 0f } ?: lastTwoFingerSpan
        val spanDelta = endSpan - twoFingerStartSpan
        val panTotal = twoFingerPanAccum
        val zoomProduct = twoFingerZoomFactorAccum
        val wasOrbitGesture = maxGesturePointerCount >= 3
        isTwoFingerActive = false
        lastTwoFingerSpan = 0f
        twoFingerStartSpan = 0f
        twoFingerPanAccum = 0f
        twoFingerZoomFactorAccum = 1f
        lastMultiTouchPointerCount = 0
        maxGesturePointerCount = 0
        glSurfaceView.queueEvent {
            renderer.persistOrientationCameraAnchor(spanDelta, panTotal, zoomProduct)
            if (wasOrbitGesture) {
                CameraDiagnostics.log(
                    renderer.camera,
                    "threeFingerEnd",
                    "spanΔ=${"%.1f".format(spanDelta)} panTotal=${"%.0f".format(panTotal)}"
                )
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!::renderer.isInitialized) return false
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            val delta = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (delta != 0f) {
                glSurfaceView.queueEvent {
                    // Zoom in for positive delta, out for negative
                    val factor = if (delta > 0) 1.1f else 0.9f
                    renderer.handleZoom(factor)
                }
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    fun showItemMenu(x: Float, y: Float, item: BumpItem) = runOnUiThread { menuManager.showItemMenu(x, y, item) }
    fun showPileMenu(x: Float, y: Float, pile: Pile, onBreak: () -> Unit) = runOnUiThread { menuManager.showPileMenu(x, y, pile, onBreak) }
    fun showRecentsMenu(x: Float, y: Float, pile: Pile) = runOnUiThread { menuManager.showRecentsMenu(x, y, pile) }
    fun showWidgetMenu(x: Float, y: Float, widget: WidgetItem) = runOnUiThread { menuManager.showWidgetMenu(x, y, widget) }
    fun showLassoMenu(x: Float, y: Float, selectedItems: List<BumpItem>) = runOnUiThread { menuManager.showLassoMenu(x, y, selectedItems) }
    fun showDesktopMenu(x: Float, y: Float) = runOnUiThread { menuManager.showDesktopMenu(x, y) }

    fun showAddToPileMenu(item: BumpItem, pile: Pile) = runOnUiThread { dialogManager.showAddToPileMenu(item, pile) }
    fun promptAddStickyNote(x: Float, y: Float) = dialogManager.promptAddStickyNote(x, y)
    fun promptEditStickyNote(item: BumpItem) = dialogManager.promptEditStickyNote(item)
    fun promptAddWebWidget(x: Float, y: Float) = dialogManager.promptAddWebWidget(x, y)
    fun promptEditWebWidget(item: BumpItem) = dialogManager.promptEditWebWidget(item)
    fun promptSearch() = dialogManager.promptSearch()
    fun promptRenamePile(pile: Pile, onRenamed: (String) -> Unit) =
        runOnUiThread { dialogManager.promptRenamePile(pile, onRenamed) }

    fun promptAddPhotoFrame(x: Float, y: Float) {
        initialTouchX = x; initialTouchY = y
        startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), REQUEST_PICK_IMAGE)
    }

    fun promptChangePhoto(item: BumpItem) {
        selectedItemForPhoto = item
        startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), REQUEST_PICK_IMAGE_FOR_FRAME)
    }

    fun saveLastTouchPosition(x: Float, y: Float) { initialTouchX = x; initialTouchY = y }

    fun createPileFromCaptured(capturedItems: List<BumpItem>, layoutMode: Pile.LayoutMode = Pile.LayoutMode.STACK) {
        if (!::renderer.isInitialized) return
        glSurfaceView.queueEvent { renderer.createPileFromCaptured(capturedItems, layoutMode) }
    }

    fun launchApp(item: BumpItem, mode: Int = WINDOWING_MODE_UNDEFINED) {
        actionHandler.launchApp(item, mode)
        glSurfaceView.postDelayed({ updateRecents() }, 1000)
    }

    fun removeTask(taskId: Int) {
        actionHandler.removeTask(taskId)
    }

    fun openWidgetPicker() {
        val id = appWidgetHost.allocateAppWidgetId()
        startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), REQUEST_PICK_APPWIDGET)
    }

    private fun loadApps() {
        val apps = appManager.loadAllApps()
        glSurfaceView.queueEvent { 
            if (::renderer.isInitialized) {
                renderer.setAllAppsList(apps) 
            }
        }
    }

    fun updateRecents() {
        appManager.refreshRecents()
    }

    fun showResetButton(show: Boolean) = runOnUiThread { btnResetView.visibility = if (show) View.VISIBLE else View.GONE }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_PICK_APPWIDGET -> data?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)?.let { if (it != -1) configureWidget(it) }
                REQUEST_CREATE_APPWIDGET -> data?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)?.let { if (it != -1) addWidgetToRenderer(it) }
                REQUEST_PICK_IMAGE -> data?.data?.let { uri ->
                    try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
                    if (::renderer.isInitialized) {
                        glSurfaceView.queueEvent { renderer.addPhotoFrame(uri.toString(), initialTouchX, initialTouchY) }
                    }
                }
                REQUEST_PICK_IMAGE_FOR_FRAME -> data?.data?.let { uri ->
                    try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
                    selectedItemForPhoto?.let { it.text = uri.toString(); it.textureId = -1 }
                }
            }
        }
    }
    private fun configureWidget(id: Int) {
        val info = appWidgetManager.getAppWidgetInfo(id)
        if (info?.configure != null) startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), REQUEST_CREATE_APPWIDGET)
        else addWidgetToRenderer(id)
    }
    private fun addWidgetToRenderer(id: Int) {
        val info = appWidgetManager.getAppWidgetInfo(id) ?: run {
            BumpDeskLog.w(BumpDeskLog.Tag.WIDGET, "addWidgetToRenderer", "no provider info id=$id")
            return
        }
        val view = appWidgetHost.createView(ContextThemeWrapper(applicationContext, R.style.Theme_BumpDesk), id, info)
        widgetContainer.addView(view)
        BumpDeskLog.d(BumpDeskLog.Tag.WIDGET, "addWidgetToRenderer", "id=$id provider=${info.provider.className}")
        if (::renderer.isInitialized) {
            glSurfaceView.queueEvent {
                renderer.addWidgetAt(id, view, initialTouchX, initialTouchY)
                glSurfaceView.post { configureWidgetHostView(id) }
            }
        }
    }

    fun configureWidgetHostView(appWidgetId: Int, invalidateTexture: Boolean = true) {
        if (!::renderer.isInitialized) return
        val view = renderer.sceneState.widgetViews[appWidgetId] ?: return
        val widget = renderer.sceneState.widgetItems.find { it.appWidgetId == appWidgetId } ?: return
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return
        widget.size = WidgetUtils.normalizeGridSize(info, widget.size)
        WidgetUtils.configureHostView(view, this, info, widget)
        if (invalidateTexture) {
            widget.textureId = -1
        }
        glSurfaceView.requestRender()
    }

    fun onWidgetResizeFinished(widget: WidgetItem) {
        if (!::renderer.isInitialized) return
        glSurfaceView.queueEvent {
            renderer.refreshWidgetAfterResize(widget)
        }
    }

    fun releaseWidgetHost(appWidgetId: Int, view: AppWidgetHostView?) {
        view?.let { widgetContainer.removeView(it) }
        WidgetCaptureCoordinator.clear(appWidgetId)
        appWidgetHost.deleteAppWidgetId(appWidgetId)
    }

    fun restoreSavedWidgets() {
        if (!::renderer.isInitialized || !::appWidgetHost.isInitialized) return
        glSurfaceView.queueEvent {
            val widgetIds = renderer.sceneState.widgetItems.map { it.appWidgetId }.toList()
            glSurfaceView.post {
                widgetIds.forEach { restoreSavedWidget(it) }
            }
        }
    }

    private fun restoreSavedWidget(id: Int) {
        if (renderer.sceneState.widgetViews.containsKey(id)) return
        val info = appWidgetManager.getAppWidgetInfo(id)
        if (info == null) {
            BumpDeskLog.w(BumpDeskLog.Tag.WIDGET, "restoreSavedWidget", "missing provider info id=$id")
            return
        }
        try {
            val view = appWidgetHost.createView(
                ContextThemeWrapper(applicationContext, R.style.Theme_BumpDesk),
                id,
                info,
            )
            widgetContainer.addView(view)
            glSurfaceView.queueEvent {
                renderer.sceneState.widgetViews[id] = view
                renderer.sceneState.widgetItems.find { it.appWidgetId == id }?.let { widget ->
                    if (widget.aspectRatio <= 0.01f) {
                        widget.aspectRatio = WidgetUtils.aspectRatioFromProvider(info)
                    }
                    if (widget.size.x <= 0.01f || widget.size.z <= 0.01f ||
                        WidgetUtils.needsAspectCorrection(info, widget.size)
                    ) {
                        widget.size = WidgetUtils.defaultWorldSize(info)
                    } else {
                        widget.size = WidgetUtils.normalizeGridSize(info, widget.size)
                    }
                    widget.textureId = -1
                }
                glSurfaceView.requestRender()
                BumpDeskLog.d(
                    BumpDeskLog.Tag.WIDGET,
                    "restoreSavedWidget",
                    "id=$id provider=${info.provider.className}",
                )
                glSurfaceView.post { configureWidgetHostView(id) }
            }
        } catch (e: Exception) {
            BumpDeskLog.fail(BumpDeskLog.Tag.WIDGET, "restoreSavedWidget", "id=$id ${e.message}", e)
        }
    }

    override fun onStart() { 
        super.onStart()
        if (::appWidgetHost.isInitialized) {
            appWidgetHost.startListening()
        }
        val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recentsReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(recentsReceiver, filter)
        }
    }
    override fun onStop() { 
        super.onStop()
        try { unregisterReceiver(recentsReceiver) } catch (e: Exception) {}
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        touchSlop = ScreenMetrics.touchSlopPx(this)
        CameraDiagnostics.logProbe(
            this,
            "configurationChanged ori=${newConfig.orientation} size=${newConfig.screenWidthDp}x${newConfig.screenHeightDp}"
        )
        if (::renderer.isInitialized) {
            CameraDiagnostics.log(renderer.camera, "configurationChanged", "beforeProfileRefresh")
            renderer.onDisplayProfileChanged()
            CameraDiagnostics.log(renderer.camera, "configurationChanged", "afterProfileRefresh")
        }
    }

    override fun onResume() { 
        super.onResume()
        glSurfaceView.onResume()
        if (::renderer.isInitialized) {
            renderer.onResume()
            val prefs = getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
            applyPendingPreferenceUpdates(prefs)
            if (prefs.getBoolean("use_wallpaper_as_floor", false) && !didReloadFloorThisResume) {
                val (cropW, cropH) = FlatFloorMode.floorCropAspectFor(this)
                if (WallpaperFloorProvider.hasBitmap()) {
                    WallpaperFloorProvider.updateFloorCropAspect(cropW, cropH)
                    renderer.reloadFloorTexture()
                } else {
                    prepareWallpaperFloorIfNeeded(prefs) {
                        renderer.reloadFloorTexture()
                    }
                }
            }
        }
        updateRecents()
    }
    override fun onPause() { 
        super.onPause()
        glSurfaceView.onPause()
    }
}
