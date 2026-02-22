package com.bass.bumpdesk

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.bass.bumpdesk.persistence.DeskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.content.Intent
import android.provider.Settings

class BumpRenderer(private val context: Context) : GLSurfaceView.Renderer {

    val sceneState = SceneState()
    val camera = CameraManager()
    val interactionManager = InteractionManager(context, camera)
    private val physicsEngine = PhysicsEngine()
    private var physicsThread: PhysicsThread? = null
    val textureManager = TextureManager(context)
    
    private lateinit var shader: DefaultShader
    private lateinit var roomRenderer: RoomRenderer
    private lateinit var overlayRenderer: OverlayRenderer
    private lateinit var lasso: Lasso

    private lateinit var itemRenderer: ItemRenderer
    private lateinit var widgetRenderer: WidgetRenderer
    private lateinit var pileRenderer: PileRenderer
    private lateinit var uiRenderer: UIRenderer

    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    private var floorTextureId = -1
    private var wallTextureIds = IntArray(4) { -1 }
    private var uiAssets = UIRenderer.UIAssets(-1, -1, -1, -1, -1)

    private val lightPos = floatArrayOf(0f, 10f, 0f)
    private var soundPool: SoundPool? = null
    private var bumpSoundId: Int = -1

    private val repository by lazy { DeskRepository(context) }
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    
    private var frameCount = 0
    var glSurfaceView: GLSurfaceView? = null
    private var searchQuery = ""

    enum class GridLayout { GRID, ROW, COLUMN }

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
        
        bumpSoundId = context.resources.getIdentifier("bump", "raw", context.packageName).let {
            if (it != 0) soundPool?.load(context, it, 1) ?: -1 else -1
        }
        updateSettings()
        startPhysics()
    }

    private fun startPhysics() {
        physicsThread = PhysicsThread(sceneState, physicsEngine) {
            if (bumpSoundId != -1) soundPool?.play(bumpSoundId, 0.1f, 0.1f, 1, 0, 1.0f)
        }
        physicsThread?.start()
    }

    fun onResume() {
        updateSettings()
        if (physicsThread == null || !physicsThread!!.isAlive) startPhysics()
    }

    fun onPause() {
        physicsThread?.stopPhysics()
        physicsThread = null
        saveState()
    }

    fun updateSettings() {
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        
        // Physics
        physicsEngine.friction = prefs.getInt("physics_friction", 94) / 100f
        physicsEngine.restitution = prefs.getInt("physics_bounciness", 25) / 100f
        physicsEngine.gravity = prefs.getInt("physics_gravity", 10) / 1000f
        
        // Layout
        physicsEngine.defaultScale = (prefs.getInt("layout_item_scale", 50) / 100f) + 0.2f
        physicsEngine.gridSpacingBase = (prefs.getInt("layout_grid_spacing", 60) / 100f) * 2.0f
    }

    private fun saveState() {
        repositoryScope.launch {
            repository.saveState(sceneState)
        }
    }

    fun loadSavedState(allApps: List<AppInfo>) {
        repositoryScope.launch {
            val items = repository.loadState(allApps)
            sceneState.bumpItems.clear()
            sceneState.bumpItems.addAll(items)
        }
    }

    fun setAllAppsList(apps: List<AppInfo>) {
        sceneState.allAppsList.clear()
        sceneState.allAppsList.addAll(apps)
        if (sceneState.appDrawerItem == null) {
            sceneState.appDrawerItem = BumpItem(type = BumpItem.Type.APP_DRAWER, position = floatArrayOf(6f, 0.05f, 6f), scale = 0.8f)
            sceneState.bumpItems.add(sceneState.appDrawerItem!!)
        }
        loadSavedState(apps)
    }

    fun addAppToDesk(app: AppInfo) {
        if (!sceneState.isAlreadyOnDesktop(app)) {
            val x = (Math.random().toFloat() * 4f) - 2f
            val z = (Math.random().toFloat() * 4f) - 2f
            sceneState.bumpItems.add(BumpItem(appInfo = app, position = floatArrayOf(x, 0.05f, z)))
        }
    }

    fun addStickyNote(text: String, x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        sceneState.bumpItems.add(BumpItem(type = BumpItem.Type.STICKY_NOTE, text = text, position = hit?.second ?: floatArrayOf(0f, 0.05f, 0f), surface = hit?.first ?: BumpItem.Surface.FLOOR, color = floatArrayOf(1f, 1f, 0.6f, 1f)))
    }

    fun addPhotoFrame(uri: String, x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        sceneState.bumpItems.add(BumpItem(type = BumpItem.Type.PHOTO_FRAME, text = uri, position = hit?.second ?: floatArrayOf(0f, 0.05f, 0f), surface = hit?.first ?: BumpItem.Surface.FLOOR, scale = 1.5f))
    }

    fun addWebWidget(url: String, x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        sceneState.bumpItems.add(BumpItem(type = BumpItem.Type.WEB_WIDGET, text = url, position = hit?.second ?: floatArrayOf(0f, 0.05f, 0f), surface = hit?.first ?: BumpItem.Surface.FLOOR, scale = 2.0f))
    }

    fun performSearch(query: String) { searchQuery = query.lowercase() }

    fun addWidgetAt(appWidgetId: Int, hostView: android.appwidget.AppWidgetHostView, x: Float, y: Float) {
        sceneState.widgetViews[appWidgetId] = hostView
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        val pos = hit?.second?.clone() ?: floatArrayOf(0f, 3f, -9.9f)
        if (hit != null) {
            when (hit.first) {
                BumpItem.Surface.BACK_WALL -> pos[2] = -9.9f
                BumpItem.Surface.LEFT_WALL -> pos[0] = -9.9f
                BumpItem.Surface.RIGHT_WALL -> pos[0] = 9.9f
                BumpItem.Surface.FLOOR -> pos[1] = 0.1f
            }
        }
        sceneState.widgetItems.add(WidgetItem(appWidgetId = appWidgetId, position = pos, surface = hit?.first ?: BumpItem.Surface.BACK_WALL))
    }

    fun removeWidget(widget: WidgetItem) {
        sceneState.widgetItems.remove(widget)
        sceneState.widgetViews.remove(widget.appWidgetId)
    }

    fun togglePin(item: BumpItem) { item.isPinned = !item.isPinned }

    fun updateRecents(recents: List<AppInfo>) {
        if (sceneState.recentsPile == null) {
            sceneState.recentsPile = Pile(mutableListOf(), floatArrayOf(0f, 4f, -9.4f), name = "Recents", layoutMode = Pile.LayoutMode.CAROUSEL, surface = BumpItem.Surface.BACK_WALL, isSystem = true)
            sceneState.piles.add(sceneState.recentsPile!!)
        }
        
        val oldItems = sceneState.recentsPile!!.items.toList()
        val newItems = recents.map { appInfo ->
            val existing = oldItems.find { it.appInfo?.packageName == appInfo.packageName } ?:
                           sceneState.bumpItems.find { it.appInfo?.packageName == appInfo.packageName } ?:
                           sceneState.piles.flatMap { it.items }.find { it.appInfo?.packageName == appInfo.packageName }
            
            val item = existing?.copy() ?: BumpItem(type = BumpItem.Type.RECENT_APP, appInfo = appInfo)
            
            item.apply {
                type = BumpItem.Type.RECENT_APP
                position = sceneState.recentsPile!!.position.clone()
                scale = 1.2f
                surface = BumpItem.Surface.BACK_WALL
                if (existing?.appInfo?.snapshot != appInfo.snapshot) {
                    textureId = -1
                }
            }
            item
        }
        
        sceneState.recentsPile!!.items.clear()
        sceneState.recentsPile!!.items.addAll(newItems)
    }

    fun reloadTheme() {
        glSurfaceView?.queueEvent { 
            textureManager.clearCache()
            sceneState.bumpItems.forEach { it.textureId = -1 }
            sceneState.piles.forEach { p -> 
                p.items.forEach { it.textureId = -1 }
                p.nameTextureId = -1
            }
            sceneState.widgetItems.forEach { it.textureId = -1 }
            // Task: Reload theme textures including UI assets to avoid black surfaces
            loadThemeTextures() 
        }
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.02f, 0.02f, 0.02f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        
        shader = DefaultShader()
        roomRenderer = RoomRenderer(shader)
        overlayRenderer = OverlayRenderer(shader)
        lasso = Lasso()

        itemRenderer = ItemRenderer(context, shader, textureManager, sceneState)
        widgetRenderer = WidgetRenderer(context, shader, textureManager)
        pileRenderer = PileRenderer(context, shader, textureManager, itemRenderer, overlayRenderer, sceneState)
        uiRenderer = UIRenderer(shader, overlayRenderer)
        
        loadThemeTextures()
    }

    private fun loadThemeTextures() {
        floorTextureId = ThemeManager.getFloorTexture(context, textureManager)
        wallTextureIds = ThemeManager.getWallTextures(context, textureManager)
        
        // Task: Ensure uiAssets is updated when loading theme textures
        uiAssets = UIRenderer.UIAssets(
            closeBtn = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("X", 64, 64)),
            arrowLeft = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap(" < ", 64, 64)),
            arrowRight = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap(" > ", 64, 64)),
            scrollUp = textureManager.loadTextureFromAsset("BumpTop/${ThemeManager.currentThemeName}/widgets/scrollUp.png"),
            scrollDown = textureManager.loadTextureFromAsset("BumpTop/${ThemeManager.currentThemeName}/widgets/scrollDown.png")
        )
    }

    override fun onDrawFrame(unused: GL10) {
        frameCount++
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        camera.update(); camera.setViewMatrix(viewMatrix)
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.invertM(interactionManager.invertedVPMatrix, 0, vPMatrix, 0)
        
        roomRenderer.draw(vPMatrix, floorTextureId, wallTextureIds, lightPos)
        
        val onUpdateTexture: (Runnable) -> Unit = { event -> glSurfaceView?.queueEvent(event) }

        itemRenderer.drawItems(vPMatrix, sceneState.bumpItems, lightPos, searchQuery, onUpdateTexture)
        widgetRenderer.drawWidgets(vPMatrix, sceneState.widgetItems, sceneState.widgetViews, frameCount, onUpdateTexture)
        pileRenderer.drawPiles(vPMatrix, sceneState.piles, lightPos, searchQuery, camera.currentViewMode, onUpdateTexture)
        uiRenderer.drawOverlays(vPMatrix, sceneState, camera, uiAssets, lightPos)
        
        if (interactionManager.lassoPoints.isNotEmpty()) lasso.draw(vPMatrix, interactionManager.lassoPoints)
    }

    fun handleTouchDown(x: Float, y: Float) = interactionManager.handleTouchDown(x, y, sceneState)
    fun handleTouchMove(x: Float, y: Float, pointerCount: Int) = interactionManager.handleTouchMove(x, y, sceneState, pointerCount)
    fun handleTouchUp() = interactionManager.handleTouchUp(sceneState) { captured -> (context as? LauncherActivity)?.showLassoMenu(interactionManager.lastTouchX, interactionManager.lastTouchY, captured) }

    fun gridSelectedItems(items: List<BumpItem>, mode: GridLayout) {
        if (items.isEmpty()) return
        val spacing = physicsEngine.gridSpacingBase; val startX = items.map { it.position[0] }.average().toFloat(); val startZ = items.map { it.position[2] }.average().toFloat()
        when (mode) {
            GridLayout.GRID -> {
                val side = Math.ceil(Math.sqrt(items.size.toDouble())).toInt(); val offset = (side * spacing) / 2f
                items.forEachIndexed { i, item -> item.position[0] = (startX - offset) + (i % side) * spacing; item.position[2] = (startZ - offset) + (i / side) * spacing; item.position[1] = 0.05f; item.surface = BumpItem.Surface.FLOOR; item.velocity.fill(0f) }
            }
            GridLayout.ROW -> {
                val offset = (items.size * spacing) / 2f
                items.forEachIndexed { i, item -> item.position[0] = (startX - offset) + i * spacing; item.position[2] = startZ; item.position[1] = 0.05f; item.surface = BumpItem.Surface.FLOOR; item.velocity.fill(0f) }
            }
            GridLayout.COLUMN -> {
                val offset = (items.size * spacing) / 2f
                items.forEachIndexed { i, item -> item.position[0] = startX; item.position[2] = (startZ - offset) + i * spacing; item.position[1] = 0.05f; item.surface = BumpItem.Surface.FLOOR; item.velocity.fill(0f) }
            }
        }
    }

    fun handleSingleTap(x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val expandedPile = sceneState.piles.find { it.isExpanded }
        if (expandedPile != null) {
            val tFloor = (2.98f - rS[1]) / (rE[1] - rS[1])
            if (tFloor > 0) {
                val iX = rS[0] + tFloor * (rE[0] - rS[0]); val iZ = rS[2] + tFloor * (rE[2] - rS[2]); val (halfDim, totalHalfDimZ, pos) = overlayRenderer.getConstrainedFolderUI(expandedPile)
                val cbX = pos[0] + halfDim - 0.2f * expandedPile.scale; val cbZ = pos[1] - totalHalfDimZ + 0.2f * expandedPile.scale
                if (Math.abs(iX - cbX) < 0.4f && Math.abs(iZ - cbZ) < 0.4f) { dismissExpandedPile(); return }
                
                if (expandedPile == sceneState.recentsPile && camera.currentViewMode == CameraManager.ViewMode.BACK_WALL) {
                    val width = 6f * expandedPile.scale
                    if (Math.abs(iX - (expandedPile.position[0] - width + 0.5f)) < 0.5f) { expandedPile.currentIndex = (expandedPile.currentIndex - 1).coerceAtLeast(0); return }
                    if (Math.abs(iX - (expandedPile.position[0] + width - 0.5f)) < 0.5f) { expandedPile.currentIndex = (expandedPile.currentIndex + 1).coerceAtMost(expandedPile.items.size - 1); return }
                }

                if (expandedPile.layoutMode == Pile.LayoutMode.GRID) {
                    val suX = pos[0] - halfDim + 0.4f * expandedPile.scale; val suZ = pos[1] - totalHalfDimZ + 0.2f * expandedPile.scale
                    if (Math.abs(iX - suX) < 0.4f && Math.abs(iZ - suZ) < 0.4f) { expandedPile.scrollIndex = (expandedPile.scrollIndex - 1).coerceAtLeast(0); return }
                    if (Math.abs(iX - (pos[0] - halfDim + 1.0f * expandedPile.scale)) < 0.4f && Math.abs(iZ - suZ) < 0.4f) { expandedPile.scrollIndex++; return }
                }
            }
        }
        val widgetHit = interactionManager.findIntersectingWidget(rS, rE, sceneState.widgetItems)
        if (widgetHit != null) { 
            interactWithWidget(widgetHit.first, rS, rE)
            if (camera.currentViewMode != CameraManager.ViewMode.WIDGET_FOCUS) { camera.focusOnWidget(widgetHit.first); (context as? LauncherActivity)?.showResetButton(true) }
            return 
        }
        val item = interactionManager.findIntersectingItem(rS, rE, sceneState.bumpItems, sceneState.piles)
        if (item != null) {
            val pile = sceneState.getPileOf(item)
            if (pile != null && pile == sceneState.recentsPile && camera.currentViewMode == CameraManager.ViewMode.BACK_WALL) {
                val t = (pile.position[2] - rS[2]) / (rE[2] - rS[2]); val u = (rS[0] + t * (rE[0] - rS[0]) - (item.position[0] - item.scale)) / (2f * item.scale); val v = 1f - (rS[1] + t * (rE[1] - rS[1]) - (item.position[1] - item.scale * 1.6f)) / (2f * item.scale * 1.6f)
                if (u > 0.85f && v < 0.15f) { sceneState.recentsPile?.items?.remove(item); return }
                if (v > 0.76f && v < 0.89f) {
                    val pkg = item.appInfo?.packageName ?: ""
                    if (u < 0.25f) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else if (u < 0.5f) (context as? LauncherActivity)?.launchApp(item, LauncherActivity.WINDOWING_MODE_FULLSCREEN)
                    else if (u < 0.75f) (context as? LauncherActivity)?.launchApp(item, LauncherActivity.WINDOWING_MODE_FREEFORM)
                    else (context as? LauncherActivity)?.launchApp(item, LauncherActivity.WINDOWING_MODE_PINNED)
                    return
                }
            }
            if (item.type == BumpItem.Type.APP && item.appInfo?.packageName == context.packageName) { context.startActivity(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
            if (pile != null && !pile.isExpanded) {
                if (pile.isSystem && pile == sceneState.recentsPile) camera.focusOnWall(CameraManager.ViewMode.BACK_WALL, floatArrayOf(0f, 4f, 2f), floatArrayOf(0f, 4f, -10f))
                else { sceneState.piles.forEach { it.isExpanded = false }; pile.isExpanded = true; camera.focusOnFolder(pile.position) }
                (context as? LauncherActivity)?.showResetButton(true); return
            }
            if (item.type == BumpItem.Type.APP_DRAWER) {
                val apps = sceneState.allAppsList
                if (apps.isNotEmpty()) {
                    val p = item.position.clone(); val dp = Pile(apps.map { BumpItem(appInfo = it, position = p.clone()) }.toMutableList(), p, name = "All Apps", isSystem = true)
                    sceneState.piles.forEach { it.isExpanded = false }; dp.isExpanded = true; sceneState.piles.add(dp); camera.focusOnFolder(p); (context as? LauncherActivity)?.showResetButton(true)
                }
                return
            }
            if (item.type == BumpItem.Type.APP || item.type == BumpItem.Type.RECENT_APP) (context as? LauncherActivity)?.launchApp(item) else if (item.type == BumpItem.Type.STICKY_NOTE) (context as? LauncherActivity)?.promptEditStickyNote(item) else if (item.type == BumpItem.Type.PHOTO_FRAME) (context as? LauncherActivity)?.promptChangePhoto(item) else if (item.type == BumpItem.Type.WEB_WIDGET) (context as? LauncherActivity)?.promptEditWebWidget(item)
        } else if (camera.currentViewMode != CameraManager.ViewMode.DEFAULT) dismissExpandedPile()
    }

    private fun interactWithWidget(widget: WidgetItem, rS: FloatArray, rE: FloatArray) {
        val view = sceneState.widgetViews[widget.appWidgetId] ?: return
        val t = when (widget.surface) { BumpItem.Surface.BACK_WALL -> (widget.position[2] - rS[2]) / (rE[2] - rS[2]); BumpItem.Surface.LEFT_WALL -> (widget.position[0] - rS[0]) / (rE[0] - rS[0]); BumpItem.Surface.RIGHT_WALL -> (widget.position[0] - rS[0]) / (rE[0] - rS[0]); else -> return }
        val iX = rS[0] + t * (rE[0] - rS[0]); val iY = rS[1] + t * (rE[1] - rS[1]); val iZ = rS[2] + t * (rE[2] - rS[2])
        val (u, v) = when (widget.surface) { BumpItem.Surface.BACK_WALL -> (iX - (widget.position[0] - widget.size[0])) / (2f * widget.size[0]) to 1f - (iY - (widget.position[1] - widget.size[1])) / (2f * widget.size[1]); BumpItem.Surface.LEFT_WALL -> (iZ - (widget.position[2] - widget.size[0])) / (2f * widget.size[0]) to 1f - (iY - (widget.position[1] - widget.size[1])) / (2f * widget.size[1]); BumpItem.Surface.RIGHT_WALL -> 1f - (iZ - (widget.position[2] - widget.size[0])) / (2f * widget.size[0]) to 1f - (iY - (widget.position[1] - widget.size[1])) / (2f * widget.size[1]); else -> return }
        view.post { val dt = android.os.SystemClock.uptimeMillis(); view.dispatchTouchEvent(android.view.MotionEvent.obtain(dt, dt, android.view.MotionEvent.ACTION_DOWN, u * view.width, v * view.height, 0)); view.dispatchTouchEvent(android.view.MotionEvent.obtain(dt, dt + 10, android.view.MotionEvent.ACTION_UP, u * view.width, v * view.height, 0)) }
    }

    fun handleDoubleTap(x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val walls = listOf(Triple(BumpItem.Surface.BACK_WALL, floatArrayOf(0f, 4f, 2f), floatArrayOf(0f, 4f, -10f)), Triple(BumpItem.Surface.LEFT_WALL, floatArrayOf(2f, 4f, 0f), floatArrayOf(-10f, 4f, 0f)), Triple(BumpItem.Surface.RIGHT_WALL, floatArrayOf(-2f, 4f, 0f), floatArrayOf(10f, 4f, 0f)))
        var best: Triple<BumpItem.Surface, FloatArray, FloatArray>? = null; var minT = Float.MAX_VALUE
        walls.forEach { (s, cp, la) -> val t = when (s) { BumpItem.Surface.BACK_WALL -> (-9.95f - rS[2]) / (rE[2] - rS[2]); BumpItem.Surface.LEFT_WALL -> (-9.95f - rS[0]) / (rE[0] - rS[0]); BumpItem.Surface.RIGHT_WALL -> (9.95f - rS[0]) / (rE[0] - rS[0]); else -> -1f }; if (t > 0 && t < minT) { if (Math.abs(rS[0] + t * (rE[0] - rS[0])) <= 10.1f && Math.abs(rS[2] + t * (rE[2] - rS[2])) <= 10.1f && (rS[1] + t * (rE[1] - rS[1])) in 0f..12f) { minT = t; best = Triple(s, cp, la) } } }
        if (best != null) { camera.focusOnWall(when(best!!.first) { BumpItem.Surface.BACK_WALL -> CameraManager.ViewMode.BACK_WALL; BumpItem.Surface.LEFT_WALL -> CameraManager.ViewMode.LEFT_WALL; else -> CameraManager.ViewMode.RIGHT_WALL }, best!!.second, best!!.third); (context as? LauncherActivity)?.showResetButton(true); return }
        val tf = -rS[1] / (rE[1] - rS[1]); if (tf > 0 && Math.abs(rS[0] + tf * (rE[0] - rS[0])) <= 10f && Math.abs(rS[2] + tf * (rE[2] - rS[2])) <= 10f) { camera.focusOnFloor(); (context as? LauncherActivity)?.showResetButton(true); return }
        handleSingleTap(x, y)
    }

    fun handleLongPress(x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val wHit = interactionManager.findIntersectingWidget(rS, rE, sceneState.widgetItems); val iHit = interactionManager.findIntersectingItem(rS, rE, sceneState.bumpItems, sceneState.piles)
        if (wHit != null && (iHit == null || wHit.second < 0.8f)) { sceneState.selectedWidget = wHit.first; (context as? LauncherActivity)?.showWidgetMenu(x, y, wHit.first); return }
        if (iHit != null) { val pile = sceneState.getPileOf(iHit); if (pile != null && !pile.isSystem) (context as? LauncherActivity)?.showPileMenu(x, y, pile) { breakPile(pile) } else (context as? LauncherActivity)?.showItemMenu(x, y, iHit) } else (context as? LauncherActivity)?.showDesktopMenu(x, y)
    }

    private fun breakPile(pile: Pile) { if (pile.isSystem) return; sceneState.piles.remove(pile); pile.items.forEach { if (!sceneState.bumpItems.contains(it)) sceneState.bumpItems.add(it); it.surface = BumpItem.Surface.FLOOR; it.position[1] = 0.05f; it.position[0] += (Math.random().toFloat() - 0.5f) * 2f; it.position[2] += (Math.random().toFloat() - 0.5f) * 2f } }
    fun resetView() { sceneState.piles.removeAll { it.isSystem && it.name == "All Apps" }; sceneState.piles.forEach { it.isExpanded = false }; camera.reset(); (context as? LauncherActivity)?.showResetButton(false) }
    fun dismissExpandedPile() { sceneState.piles.removeAll { it.isSystem && it.name == "All Apps" }; sceneState.piles.forEach { it.isExpanded = false }; camera.restorePreviousView(); (context as? LauncherActivity)?.showResetButton(camera.currentViewMode != CameraManager.ViewMode.DEFAULT) }
    fun handleZoom(sf: Float) { camera.zoomLevel = (camera.zoomLevel / sf).coerceIn(0.5f, 2.0f); if (camera.zoomLevel != 1.0f) (context as? LauncherActivity)?.showResetButton(true) }
    fun handlePan(dx: Float, dz: Float) { camera.handlePan(dx, dz); (context as? LauncherActivity)?.showResetButton(true) }
    fun handlePan(dx: Float, dz: Float, dummy: Boolean) { camera.handlePan(dx, dz); (context as? LauncherActivity)?.showResetButton(true) }
    override fun onSurfaceChanged(unused: GL10, w: Int, h: Int) { GLES20.glViewport(0, 0, w, h); interactionManager.screenWidth = w; interactionManager.screenHeight = h; Matrix.perspectiveM(projectionMatrix, 0, 60f, w.toFloat() / h, 0.1f, 100f) }
}
