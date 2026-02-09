package com.bass.bumpdesk

import android.app.ActivityOptions
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.*
import android.widget.Button
import android.widget.EditText
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

    private var isScaling = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
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

        btnResetView = findViewById(R.id.btnResetView)
        radialMenu = findViewById(R.id.radialMenu)
        widgetContainer = findViewById(R.id.widgetContainer)
        menuManager = MenuManager(this, radialMenu, glSurfaceView, renderer, this)
        
        btnResetView.setOnClickListener {
            glSurfaceView.queueEvent { renderer.resetView() }
        }

        setupGestures()
        loadApps()
        handleIntent(intent)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "selected_theme" || key == "use_wallpaper_as_floor" || key == "show_recent_apps") {
            ThemeManager.init(this, forceReload = true)
            renderer.reloadTheme()
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
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == ACTION_RECENTS) {
            glSurfaceView.queueEvent {
                renderer.sceneState.recentsPile?.let {
                    renderer.camera.focusOnWall(CameraManager.ViewMode.BACK_WALL, floatArrayOf(0f, 4f, 2f), floatArrayOf(0f, 4f, -10f))
                    showResetButton(true)
                }
            }
        }
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
                isScaling = false
            }
        })

        glSurfaceView.setOnTouchListener { _, event ->
            if (radialMenu.visibility == View.VISIBLE) {
                return@setOnTouchListener radialMenu.dispatchTouchEvent(event)
            }
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> glSurfaceView.queueEvent { renderer.handleTouchDown(event.x, event.y) }
                MotionEvent.ACTION_MOVE -> glSurfaceView.queueEvent { renderer.handleTouchMove(event.x, event.y, event.pointerCount) }
                MotionEvent.ACTION_UP -> glSurfaceView.queueEvent { renderer.handleTouchUp() }
            }
            true
        }
    }

    fun showItemMenu(x: Float, y: Float, item: BumpItem) = runOnUiThread { menuManager.showItemMenu(x, y, item) }
    fun showPileMenu(x: Float, y: Float, pile: Pile, onBreak: () -> Unit) = runOnUiThread { menuManager.showPileMenu(x, y, pile, onBreak) }
    fun showWidgetMenu(x: Float, y: Float, widget: WidgetItem) = runOnUiThread { menuManager.showWidgetMenu(x, y, widget) }
    fun showLassoMenu(x: Float, y: Float, selectedItems: List<BumpItem>) = runOnUiThread { 
        menuManager.showLassoMenu(x, y, selectedItems)
    }
    fun showDesktopMenu(x: Float, y: Float) = runOnUiThread { menuManager.showDesktopMenu(x, y) }

    fun showAddToPileMenu(item: BumpItem, pile: Pile) = runOnUiThread {
        AlertDialog.Builder(this)
            .setTitle("Add to Stack?")
            .setMessage("Do you want to add ${item.appInfo?.label ?: "this item"} to ${pile.name}?")
            .setPositiveButton("Yes") { _, _ ->
                glSurfaceView.queueEvent {
                    renderer.sceneState.bumpItems.remove(item)
                    pile.items.add(item)
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    fun promptAddStickyNote(x: Float, y: Float) = runOnUiThread {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Add Sticky Note")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    glSurfaceView.queueEvent { renderer.addStickyNote(text, x, y) }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun promptEditStickyNote(item: BumpItem) = runOnUiThread {
        val input = EditText(this).apply { setText(item.text) }
        AlertDialog.Builder(this)
            .setTitle("Edit Note")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val text = input.text.toString()
                glSurfaceView.queueEvent {
                    item.text = text
                    item.textureId = -1
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun promptAddPhotoFrame(x: Float, y: Float) {
        lastTouchX = x
        lastTouchY = y
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_PICK_IMAGE)
    }

    fun promptChangePhoto(item: BumpItem) {
        selectedItemForPhoto = item
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_PICK_IMAGE_FOR_FRAME)
    }

    fun promptAddWebWidget(x: Float, y: Float) = runOnUiThread {
        val input = EditText(this).apply { setText("https://") }
        AlertDialog.Builder(this)
            .setTitle("Add Web Widget")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val url = input.text.toString()
                if (url.isNotBlank()) {
                    glSurfaceView.queueEvent { renderer.addWebWidget(url, x, y) }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun promptEditWebWidget(item: BumpItem) = runOnUiThread {
        val input = EditText(this).apply { setText(item.text) }
        AlertDialog.Builder(this)
            .setTitle("Edit URL")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val url = input.text.toString()
                glSurfaceView.queueEvent {
                    item.text = url
                    item.textureId = -1
                    renderer.sceneState.webViews.remove(item.hashCode())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun promptSearch() = runOnUiThread {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Search Desk")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString()
                glSurfaceView.queueEvent { renderer.performSearch(query) }
            }
            .setNegativeButton("Clear") { _, _ ->
                glSurfaceView.queueEvent { renderer.performSearch("") }
            }
            .show()
    }

    fun saveLastTouchPosition(x: Float, y: Float) {
        lastTouchX = x
        lastTouchY = y
    }

    fun createPileFromCaptured(capturedItems: List<BumpItem>) {
        renderer.sceneState.piles.forEach { pile -> pile.items.removeAll(capturedItems) }
        renderer.sceneState.piles.removeAll { it.items.size < 2 && !it.isSystem }
        val pPos = floatArrayOf(capturedItems.map { it.position[0] }.average().toFloat(), 0.05f, capturedItems.map { it.position[2] }.average().toFloat())
        renderer.sceneState.piles.add(Pile(capturedItems.toMutableList(), pPos))
    }

    fun launchApp(item: BumpItem, windowingMode: Int = WINDOWING_MODE_UNDEFINED) {
        val packageName = item.appInfo?.packageName ?: return
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        val options = ActivityOptions.makeBasic()
        if (windowingMode != WINDOWING_MODE_UNDEFINED) {
            try {
                val setLaunchWindowingModeMethod = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                setLaunchWindowingModeMethod.invoke(options, windowingMode)
                
                if (windowingMode == WINDOWING_MODE_FREEFORM) {
                    val dm = resources.displayMetrics
                    val rect = Rect(dm.widthPixels/4, dm.heightPixels/4, dm.widthPixels*3/4, dm.heightPixels*3/4)
                    val setLaunchBoundsMethod = ActivityOptions::class.java.getMethod("setLaunchBounds", Rect::class.java)
                    setLaunchBoundsMethod.invoke(options, rect)
                }
            } catch (e: Exception) {
                if (windowingMode == WINDOWING_MODE_FREEFORM) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
            }
        }
        startActivity(intent, options.toBundle())
        glSurfaceView.postDelayed({ updateRecents() }, 1000)
    }

    fun promptRenamePile(pile: Pile, onRenamed: (String) -> Unit) = runOnUiThread {
        val input = EditText(this).apply { setText(pile.name); setSelection(pile.name.length) }
        AlertDialog.Builder(this).setTitle("Rename Folder").setView(input).setPositiveButton("OK") { _, _ ->
            val newName = input.text.toString()
            if (newName.isNotBlank()) glSurfaceView.queueEvent { onRenamed(newName) }
        }.setNegativeButton("Cancel", null).show()
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
                REQUEST_PICK_APPWIDGET -> {
                    data?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)?.let { id ->
                        if (id != -1) configureWidget(id)
                    }
                }
                REQUEST_CREATE_APPWIDGET -> {
                    data?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)?.let { id ->
                        if (id != -1) addWidgetToRenderer(id)
                    }
                }
                REQUEST_PICK_IMAGE -> {
                    data?.data?.let { uri ->
                        try {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: SecurityException) {}
                        glSurfaceView.queueEvent { renderer.addPhotoFrame(uri.toString(), lastTouchX, lastTouchY) }
                    }
                }
                REQUEST_PICK_IMAGE_FOR_FRAME -> {
                    data?.data?.let { uri ->
                        try {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: SecurityException) {}
                        selectedItemForPhoto?.let { item ->
                            glSurfaceView.queueEvent {
                                item.text = uri.toString()
                                item.textureId = -1
                            }
                        }
                    }
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
