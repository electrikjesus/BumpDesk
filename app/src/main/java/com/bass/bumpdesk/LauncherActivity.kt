package com.bass.bumpdesk

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
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

class LauncherActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: BumpRenderer
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    
    private lateinit var btnResetView: Button
    private lateinit var radialMenu: RadialMenuView
    private lateinit var widgetContainer: FrameLayout
    
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    
    private lateinit var appManager: AppManager
    private lateinit var menuManager: MenuManager
    
    private lateinit var dialogManager: DialogManager
    private lateinit var actionHandler: ActionHandler

    private var isScaling = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f
    private var selectedItemForPhoto: BumpItem? = null

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
        prefs.registerOnSharedPreferenceChangeListener(this)

        if (!prefs.getBoolean("onboarding_complete", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupSystemUi()

        setContentView(R.layout.activity_launcher)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        appManager = AppManager(this)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.preserveEGLContextOnPause = true

        renderer = BumpRenderer(this)
        renderer.glSurfaceView = glSurfaceView
        glSurfaceView.setRenderer(renderer)

        dialogManager = DialogManager(this, glSurfaceView, renderer)
        actionHandler = ActionHandler(this, glSurfaceView, renderer)

        btnResetView = findViewById(R.id.btnResetView)
        radialMenu = findViewById(R.id.radialMenu)
        widgetContainer = findViewById(R.id.widgetContainer)
        menuManager = MenuManager(this, radialMenu, glSurfaceView, renderer, this)
        
        btnResetView.setOnClickListener {
            glSurfaceView.queueEvent { renderer.resetView() }
        }

        setupGestures()
        loadApps()
        actionHandler.handleIntent(intent) { showResetButton(it) }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "selected_theme" || key == "use_wallpaper_as_floor" || key == "show_recent_apps" || key?.startsWith("physics_") == true || key?.startsWith("layout_") == true) {
            ThemeManager.init(this, forceReload = true)
            renderer.reloadTheme()
            renderer.updateSettings()
            if (key == "show_recent_apps") {
                updateRecents()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val prefs = getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
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
                if (radialMenu.visibility == View.VISIBLE) return true
                glSurfaceView.queueEvent { renderer.handleDoubleTap(e.x, e.y) }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (radialMenu.visibility == View.VISIBLE) return true
                glSurfaceView.queueEvent { renderer.handleSingleTap(e.x, e.y) }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (radialMenu.visibility == View.VISIBLE) return
                glSurfaceView.queueEvent { renderer.handleLongPress(e.x, e.y) }
            }
            
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (radialMenu.visibility == View.VISIBLE || isScaling) return true
                return false
            }
        })
        
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (radialMenu.visibility == View.VISIBLE) return true
                glSurfaceView.queueEvent { renderer.handleZoom(detector.scaleFactor) }
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                glSurfaceView.postDelayed({ isScaling = false }, 100)
            }
        })

        glSurfaceView.setOnTouchListener { _, event ->
            if (radialMenu.visibility == View.VISIBLE) {
                return@setOnTouchListener radialMenu.dispatchTouchEvent(event)
            }
            
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            
            val pointerCount = event.pointerCount
            
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (pointerCount == 2) {
                        lastMidX = (event.getX(0) + event.getX(1)) / 2f
                        lastMidY = (event.getY(0) + event.getY(1)) / 2f
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pointerCount == 2 && !isScaling) {
                        val midX = (event.getX(0) + event.getX(1)) / 2f
                        val midY = (event.getY(0) + event.getY(1)) / 2f
                        val dx = midX - lastMidX
                        val dy = midY - lastMidY
                        glSurfaceView.queueEvent { renderer.handlePan(dx, dy) }
                        lastMidX = midX
                        lastMidY = midY
                    } else if (pointerCount == 1 && !isScaling) {
                        glSurfaceView.queueEvent { renderer.handleTouchMove(event.x, event.y, pointerCount) }
                    }
                }
                MotionEvent.ACTION_DOWN -> {
                    glSurfaceView.queueEvent { renderer.handleTouchDown(event.x, event.y) }
                }
                MotionEvent.ACTION_UP -> {
                    glSurfaceView.queueEvent { renderer.handleTouchUp() }
                }
            }

            true
        }
    }

    fun showItemMenu(x: Float, y: Float, item: BumpItem) = runOnUiThread { menuManager.showItemMenu(x, y, item) }
    fun showPileMenu(x: Float, y: Float, pile: Pile, onBreak: () -> Unit) = runOnUiThread { menuManager.showPileMenu(x, y, pile, onBreak) }
    fun showWidgetMenu(x: Float, y: Float, widget: WidgetItem) = runOnUiThread { menuManager.showWidgetMenu(x, y, widget) }
    fun showLassoMenu(x: Float, y: Float, selectedItems: List<BumpItem>) = runOnUiThread { menuManager.showLassoMenu(x, y, selectedItems) }
    fun showDesktopMenu(x: Float, y: Float) = runOnUiThread { menuManager.showDesktopMenu(x, y) }

    fun showAddToPileMenu(item: BumpItem, pile: Pile) = dialogManager.showAddToPileMenu(item, pile)
    fun promptAddStickyNote(x: Float, y: Float) = dialogManager.promptAddStickyNote(x, y)
    fun promptEditStickyNote(item: BumpItem) = dialogManager.promptEditStickyNote(item)
    fun promptAddWebWidget(x: Float, y: Float) = dialogManager.promptAddWebWidget(x, y)
    fun promptEditWebWidget(item: BumpItem) = dialogManager.promptEditWebWidget(item)
    fun promptSearch() = dialogManager.promptSearch()
    fun promptRenamePile(pile: Pile, onRenamed: (String) -> Unit) = dialogManager.promptRenamePile(pile, onRenamed)

    fun promptAddPhotoFrame(x: Float, y: Float) {
        lastTouchX = x; lastTouchY = y
        startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), REQUEST_PICK_IMAGE)
    }

    fun promptChangePhoto(item: BumpItem) {
        selectedItemForPhoto = item
        startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), REQUEST_PICK_IMAGE_FOR_FRAME)
    }

    fun saveLastTouchPosition(x: Float, y: Float) { lastTouchX = x; lastTouchY = y }

    fun createPileFromCaptured(capturedItems: List<BumpItem>) {
        renderer.sceneState.piles.forEach { it.items.removeAll(capturedItems) }
        renderer.sceneState.piles.removeAll { it.items.size < 2 && !it.isSystem }
        val pPos = floatArrayOf(capturedItems.map { it.position[0] }.average().toFloat(), 0.05f, capturedItems.map { it.position[2] }.average().toFloat())
        renderer.sceneState.piles.add(Pile(capturedItems.toMutableList(), pPos))
    }

    fun launchApp(item: BumpItem, mode: Int = WINDOWING_MODE_UNDEFINED) {
        actionHandler.launchApp(item, mode)
        glSurfaceView.postDelayed({ updateRecents() }, 1000)
    }

    fun openWidgetPicker() {
        val id = appWidgetHost.allocateAppWidgetId()
        startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), REQUEST_PICK_APPWIDGET)
    }

    private fun loadApps() {
        val apps = appManager.loadAllApps()
        glSurfaceView.queueEvent { renderer.setAllAppsList(apps) }
    }

    fun updateRecents() {
        val recents = appManager.getRecentApps()
        glSurfaceView.queueEvent { renderer.updateRecents(recents) }
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
                    glSurfaceView.queueEvent { renderer.addPhotoFrame(uri.toString(), lastTouchX, lastTouchY) }
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
        val info = appWidgetManager.getAppWidgetInfo(id) ?: return
        val view = appWidgetHost.createView(ContextThemeWrapper(applicationContext, R.style.Theme_BumpDesk), id, info)
        widgetContainer.addView(view)
        glSurfaceView.queueEvent { renderer.addWidgetAt(id, view, lastTouchX, lastTouchY) }
    }

    override fun onStart() { super.onStart(); appWidgetHost.startListening() }
    override fun onStop() { super.onStop(); appWidgetHost.stopListening() }
    override fun onResume() { super.onResume(); glSurfaceView.onResume(); updateRecents() }
    override fun onPause() { super.onPause(); glSurfaceView.onPause() }
}
