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
import kotlin.math.abs
import kotlin.math.ceil

class BumpRenderer(private val context: Context) : GLSurfaceView.Renderer {

    val sceneState = SceneState()
    val camera = CameraManager()
    val interactionManager = InteractionManager(context, camera)
    private val physicsEngine = PhysicsEngine()
    private var physicsThread: PhysicsThread? = null
    val textureManager = TextureManager(context)
    val hapticManager = HapticManager(context)
    
    private lateinit var shader: DefaultShader
    private lateinit var roomRenderer: RoomRenderer
    private lateinit var overlayRenderer: OverlayRenderer
    private lateinit var lassoRenderer: LassoRenderer

    private lateinit var itemRenderer: ItemRenderer
    private lateinit var widgetRenderer: WidgetRenderer
    private lateinit var pileRenderer: PileRenderer
    private lateinit var uiRenderer: UIRenderer

    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    @Volatile
    private var floorTextureId = -1
    @Volatile
    private var wallTextureIds = IntArray(4) { -1 }
    private var uiAssets = UIRenderer.UIAssets(-1, -1, -1, -1, -1)

    private val lightPos = floatArrayOf(0f, 10f, 0f)
    private var soundPool: SoundPool? = null
    private var bumpSoundId: Int = -1
    private var selectionSoundId: Int = -1
    private var expandSoundId: Int = -1
    private var focusSoundId: Int = -1
    private var leafSoundId: Int = -1
    private var lassoSoundId: Int = -1

    private val repository by lazy { DeskRepository(context) }
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    
    private var frameCount = 0
    var glSurfaceView: GLSurfaceView? = null
    private var searchQuery = ""
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    enum class GridLayout { GRID, ROW, COLUMN }

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()
        
        loadSounds()
        updateSettings()
        startPhysics()
    }

    private fun loadSounds() {
        bumpSoundId = loadSound("bump")
        selectionSoundId = loadSound("select")
        expandSoundId = loadSound("expand")
        focusSoundId = loadSound("focus")
        leafSoundId = loadSound("leaf")
        lassoSoundId = loadSound("lasso")
    }

    private fun loadSound(name: String): Int {
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        return if (id != 0) soundPool?.load(context, id, 1) ?: -1 else -1
    }

    fun playSound(soundId: Int, volume: Float = 1.0f) {
        if (soundId != -1) {
            soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
        }
    }

    private fun startPhysics() {
        physicsThread = PhysicsThread(sceneState, physicsEngine) { magnitude ->
            val vol = (magnitude * 2.0f).coerceIn(0.05f, 1.0f)
            playSound(bumpSoundId, vol)
            if (magnitude > 0.5f) {
                hapticManager.heavyImpact(magnitude)
            }
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
        physicsEngine.friction = prefs.getInt("physics_friction", 94) / 100f
        physicsEngine.restitution = prefs.getInt("physics_bounciness", 25) / 100f
        physicsEngine.gravity = prefs.getInt("physics_gravity", 10) / 1000f
        physicsEngine.defaultScale = (prefs.getInt("layout_item_scale", 50) / 100f) + 0.2f
        physicsEngine.gridSpacingBase = (prefs.getInt("layout_grid_spacing", 60) / 100f) * 2.0f
        
        val showAppDrawer = prefs.getBoolean("show_app_drawer_icon", true)
        val hasAppDrawer = sceneState.bumpItems.any { it.appearance.type == BumpItem.Type.APP_DRAWER } ||
                           sceneState.piles.any { p -> p.items.any { it.appearance.type == BumpItem.Type.APP_DRAWER } }
        
        if (showAppDrawer && !hasAppDrawer) {
            sceneState.appDrawerItem = BumpItem(type = BumpItem.Type.APP_DRAWER, position = Vector3(6f, 0.05f, 6f), scale = 0.8f)
            sceneState.bumpItems.add(sceneState.appDrawerItem!!)
        } else if (!showAppDrawer && hasAppDrawer) {
            sceneState.bumpItems.removeAll { it.appearance.type == BumpItem.Type.APP_DRAWER }
            sceneState.piles.forEach { it.items.removeAll { item -> item.appearance.type == BumpItem.Type.APP_DRAWER } }
            sceneState.appDrawerItem = null
        }
        
        val oldInfinite = physicsEngine.isInfiniteMode
        physicsEngine.isInfiniteMode = prefs.getBoolean("infinite_desktop_mode", false)
        interactionManager.isInfiniteMode = physicsEngine.isInfiniteMode
        
        // Force immediate reload of theme textures to ensure floor updates when infinite mode changes
        if (oldInfinite != physicsEngine.isInfiniteMode) {
            reloadTheme()
        }
        
        glSurfaceView?.requestRender()
    }

    private fun saveState() {
        repositoryScope.launch {
            repository.saveState(sceneState)
        }
    }

    fun loadSavedState(allApps: List<AppInfo>, onComplete: () -> Unit = {}) {
        repositoryScope.launch {
            val result = repository.loadState(allApps)
            val bumpItems = result.first
            val widgetItems = result.second
            val piles = result.third
            
            sceneState.withWriteLock {
                sceneState.bumpItems.clear()
                sceneState.bumpItems.addAll(bumpItems)
                sceneState.widgetItems.clear()
                sceneState.widgetItems.addAll(widgetItems)
                sceneState.piles.clear()
                sceneState.piles.addAll(piles)
                
                // Restore special pointers
                sceneState.recentsPile = sceneState.piles.find { it.isSystem && it.name == "Recents" }
                sceneState.appDrawerItem = sceneState.bumpItems.find { it.appearance.type == BumpItem.Type.APP_DRAWER }
            }
            
            onComplete()
            glSurfaceView?.requestRender()
        }
    }

    fun setAllAppsList(apps: List<AppInfo>) {
        sceneState.allAppsList.clear()
        sceneState.allAppsList.addAll(apps)
        
        loadSavedState(apps) {
            updateSettings()
        }
    }

    fun addAppToDesk(app: AppInfo) {
        if (!sceneState.isAlreadyOnDesktop(app)) {
            val x = (Math.random().toFloat() * 4f) - 2f
            val z = (Math.random().toFloat() * 4f) - 2f
            sceneState.bumpItems.add(BumpItem(appInfo = app, position = Vector3(x, 0.05f, z)))
            saveState()
        }
    }

    fun addStickyNote(text: String, x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        val pos = if (hit != null) Vector3.fromArray(hit.second) else Vector3(0f, 0.05f, 0f)
        sceneState.bumpItems.add(BumpItem(type = BumpItem.Type.STICKY_NOTE, text = text, position = pos, surface = hit?.first ?: BumpItem.Surface.FLOOR, color = floatArrayOf(1f, 1f, 0.6f, 1f)))
        saveState()
    }

    fun addPhotoFrame(uri: String, x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        val pos = if (hit != null) Vector3.fromArray(hit.second) else Vector3(0f, 0.05f, 0f)
        sceneState.bumpItems.add(BumpItem(type = BumpItem.Type.PHOTO_FRAME, text = uri, position = pos, surface = hit?.first ?: BumpItem.Surface.FLOOR, scale = 1.5f))
        saveState()
    }

    fun addWebWidget(url: String, x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        val pos = if (hit != null) Vector3.fromArray(hit.second) else Vector3(0f, 0.05f, 0f)
        sceneState.bumpItems.add(BumpItem(type = BumpItem.Type.WEB_WIDGET, text = url, position = pos, surface = hit?.first ?: BumpItem.Surface.FLOOR, scale = 2.0f))
        saveState()
    }

    fun performSearch(query: String) { searchQuery = query.lowercase(); glSurfaceView?.requestRender() }

    fun addWidgetAt(appWidgetId: Int, hostView: android.appwidget.AppWidgetHostView, x: Float, y: Float) {
        sceneState.widgetViews[appWidgetId] = hostView
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        val rawPos = hit?.second?.clone() ?: floatArrayOf(0f, 3f, -9.9f)
        if (hit != null) {
            when (hit.first) {
                BumpItem.Surface.BACK_WALL -> rawPos[2] = -9.9f
                BumpItem.Surface.LEFT_WALL -> rawPos[0] = -9.9f
                BumpItem.Surface.RIGHT_WALL -> rawPos[0] = 9.9f
                BumpItem.Surface.FLOOR -> rawPos[1] = 0.1f
            }
        }
        sceneState.widgetItems.add(WidgetItem(appWidgetId = appWidgetId, position = Vector3.fromArray(rawPos), surface = hit?.first ?: BumpItem.Surface.BACK_WALL))
        saveState()
    }

    fun removeWidget(widget: WidgetItem) {
        sceneState.widgetItems.remove(widget)
        sceneState.widgetViews.remove(widget.appWidgetId)
        saveState()
    }

    fun togglePin(item: BumpItem) { item.transform.isPinned = !item.transform.isPinned; saveState() }

    fun updateRecents(recents: List<AppInfo>) {
        if (sceneState.recentsPile == null) {
            sceneState.recentsPile = Pile(mutableListOf(), Vector3(0f, 4f, -9.4f), name = "Recents", layoutMode = Pile.LayoutMode.CAROUSEL, surface = BumpItem.Surface.BACK_WALL, isSystem = true)
            sceneState.piles.add(sceneState.recentsPile!!)
        }
        
        val oldItems = sceneState.recentsPile!!.items.toList()
        val newItems = recents.map { appInfo ->
            val existing = oldItems.find { it.appData?.appInfo?.packageName == appInfo.packageName } ?:
                           sceneState.bumpItems.find { it.appData?.appInfo?.packageName == appInfo.packageName } ?:
                           sceneState.piles.flatMap { it.items }.find { it.appData?.appInfo?.packageName == appInfo.packageName }
            
            val item = existing?.copy() ?: BumpItem(type = BumpItem.Type.RECENT_APP, appInfo = appInfo)
            
            item.apply {
                appearance.type = BumpItem.Type.RECENT_APP
                transform.position = sceneState.recentsPile!!.position.copy()
                transform.scale = 1.2f
                transform.surface = BumpItem.Surface.BACK_WALL
                if (existing?.appData?.appInfo?.snapshot != appInfo.snapshot) {
                    appearance.textureId = -1
                }
            }
            item
        }
        
        sceneState.recentsPile!!.items.clear()
        sceneState.recentsPile!!.items.addAll(newItems)
        glSurfaceView?.requestRender()
    }

    fun categorizeAllApps() {
        val apps = sceneState.bumpItems.filter { it.appearance.type == BumpItem.Type.APP && it.appData?.appInfo != null }
        if (apps.isEmpty()) return
        
        val groups = apps.groupBy { it.appData?.appInfo?.category ?: AppInfo.Category.OTHER }
        
        sceneState.bumpItems.removeAll(apps)
        
        var pileIdx = 0
        groups.forEach { (category, items) ->
            if (items.size < 2) {
                sceneState.bumpItems.addAll(items)
                return@forEach
            }
            
            val posX = (pileIdx % 3) * 4f - 4f
            val posZ = (pileIdx / 3) * 4f - 4f
            val pile = Pile(
                items = items.toMutableList(),
                position = Vector3(posX, 0.05f, posZ),
                name = category.name.lowercase().replaceFirstChar { it.uppercase() },
                surface = BumpItem.Surface.FLOOR
            )
            sceneState.piles.add(pile)
            pileIdx++
        }
        
        playSound(expandSoundId, 0.5f)
        hapticManager.selection()
        saveState()
    }

    fun reloadTheme() {
        saveState()
        val reloadTask = Runnable {
            textureManager.clearCache()
            // Reset class texture state to force immediate refresh from ThemeManager
            floorTextureId = -1
            wallTextureIds = IntArray(4) { -1 }
            
            sceneState.withReadLock {
                sceneState.bumpItems.forEach { it.appearance.textureId = -1 }
                sceneState.piles.forEach { p -> 
                    p.items.forEach { it.appearance.textureId = -1 }
                    p.nameTextureId = -1
                }
                sceneState.widgetItems.forEach { it.textureId = -1 }
            }
            loadThemeTextures() 
            glSurfaceView?.requestRender()
        }

        if (Thread.currentThread().name.contains("GLThread")) {
            reloadTask.run()
        } else {
            glSurfaceView?.queueEvent(reloadTask)
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
        lassoRenderer = LassoRenderer(LassoShader())

        itemRenderer = ItemRenderer(context, shader, textureManager, sceneState)
        widgetRenderer = WidgetRenderer(context, shader, textureManager)
        pileRenderer = PileRenderer(context, shader, textureManager, itemRenderer, overlayRenderer, sceneState)
        uiRenderer = UIRenderer(shader, overlayRenderer)
        
        loadThemeTextures()
    }

    private fun loadThemeTextures() {
        floorTextureId = ThemeManager.getFloorTexture(context, textureManager)
        wallTextureIds = ThemeManager.getWallTextures(context, textureManager)
        
        // Ensure textures are non-zero/valid to fix white square issues
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
        
        // Update projection matrix if FOV has changed
        Matrix.perspectiveM(projectionMatrix, 0, camera.fieldOfView, surfaceWidth.toFloat() / surfaceHeight, 0.1f, 100f)
        
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.invertM(interactionManager.invertedVPMatrix, 0, vPMatrix, 0)
        
        roomRenderer.draw(vPMatrix, floorTextureId, wallTextureIds, lightPos, interactionManager.isInfiniteMode)
        
        val onUpdateTexture: (Runnable) -> Unit = { event -> glSurfaceView?.queueEvent(event) }

        itemRenderer.drawItems(vPMatrix, sceneState.bumpItems, lightPos, searchQuery, onUpdateTexture)
        widgetRenderer.drawWidgets(vPMatrix, sceneState.widgetItems, sceneState.widgetViews, frameCount, sceneState.selectedWidget, onUpdateTexture)
        pileRenderer.drawPiles(vPMatrix, sceneState.piles, lightPos, searchQuery, camera.currentViewMode, onUpdateTexture)
        uiRenderer.drawOverlays(vPMatrix, sceneState, camera, uiAssets, lightPos, searchQuery, textureManager)
        
        if (interactionManager.lassoPoints.isNotEmpty()) lassoRenderer.draw(vPMatrix, interactionManager.lassoPoints)
    }

    fun handleTouchDown(x: Float, y: Float) {
        val hit = interactionManager.handleTouchDown(x, y, sceneState)
        if (hit != null) {
            playSound(selectionSoundId, 0.2f)
            hapticManager.selection()
        }
    }
    
    fun handleTouchMove(x: Float, y: Float, pointerCount: Int) {
        val eventConsumed = interactionManager.handleTouchMove(x, y, sceneState, pointerCount)
        if (eventConsumed) {
            playSound(leafSoundId, 0.15f)
            hapticManager.tick()
        }
    }

    fun handleTouchUp() {
        interactionManager.handleTouchUp(sceneState) { captured -> 
            if (captured.isNotEmpty()) {
                playSound(lassoSoundId, 0.4f)
                hapticManager.selection()
            }
            (context as? LauncherActivity)?.showLassoMenu(interactionManager.lastTouchX, interactionManager.lastTouchY, captured) 
        }
        saveState()
    }

    fun gridSelectedItems(items: List<BumpItem>, mode: GridLayout) {
        if (items.isEmpty()) return
        val spacing = physicsEngine.gridSpacingBase; val startX = items.map { it.transform.position.x }.average().toFloat(); val startZ = items.map { it.transform.position.z }.average().toFloat()
        when (mode) {
            GridLayout.GRID -> {
                val side = Math.ceil(Math.sqrt(items.size.toDouble())).toInt(); val offset = (side * spacing) / 2f
                items.forEachIndexed { i, item -> 
                    item.transform.position = Vector3((startX - offset) + (i % side) * spacing, 0.05f, (startZ - offset) + (i / side) * spacing)
                    item.transform.surface = BumpItem.Surface.FLOOR
                    item.transform.velocity = Vector3()
                }
            }
            GridLayout.ROW -> {
                val offset = (items.size * spacing) / 2f
                items.forEachIndexed { i, item -> 
                    item.transform.position = Vector3((startX - offset) + i * spacing, 0.05f, startZ)
                    item.transform.surface = BumpItem.Surface.FLOOR
                    item.transform.velocity = Vector3()
                }
            }
            GridLayout.COLUMN -> {
                val offset = (items.size * spacing) / 2f
                items.forEachIndexed { i, item -> 
                    item.transform.position = Vector3(startX, 0.05f, (startZ - offset) + i * spacing)
                    item.transform.surface = BumpItem.Surface.FLOOR
                    item.transform.velocity = Vector3()
                }
            }
        }
        saveState()
    }

    fun handleSingleTap(x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val expandedPile = sceneState.piles.find { it.isExpanded }
        if (expandedPile != null) {
            // Task: Support hit detection on Wall-mounted folders (Recents)
            val isWall = expandedPile.surface != BumpItem.Surface.FLOOR
            val t = if (isWall) {
                (expandedPile.position.z - rS[2]) / (rE[2] - rS[2])
            } else {
                (2.90f - rS[1]) / (rE[1] - rS[1]) // Matches OverlayRenderer floor plane height
            }

            if (t > 0) {
                val hitX = rS[0] + t * (rE[0] - rS[0])
                val hitY = rS[1] + t * (rE[1] - rS[1])
                val hitZ = rS[2] + t * (rE[2] - rS[2])
                
                val uiData = overlayRenderer.getConstrainedFolderUI(expandedPile)
                val halfDimX = uiData.halfDimX
                val halfDimZ = uiData.halfDimZ
                val pos = uiData.pos

                if (isWall) {
                    val width = 6f * expandedPile.scale
                    val height = 4f * expandedPile.scale
                    // Arrow Left
                    if (abs(hitX - (expandedPile.position.x - width + 0.5f)) < 0.5f && abs(hitY - expandedPile.position.y) < 0.5f) {
                        expandedPile.currentIndex = (expandedPile.currentIndex - 1).coerceAtLeast(0)
                        playSound(leafSoundId, 0.2f); hapticManager.tick(); return
                    }
                    // Arrow Right
                    if (abs(hitX - (expandedPile.position.x + width - 0.5f)) < 0.5f && abs(hitY - expandedPile.position.y) < 0.5f) {
                        expandedPile.currentIndex = (expandedPile.currentIndex + 1).coerceAtMost(expandedPile.items.size - 1)
                        playSound(leafSoundId, 0.2f); hapticManager.tick(); return
                    }
                    // Close check for Recents (usually top corner or clicking outside)
                    if (abs(hitX - expandedPile.position.x) > width || abs(hitY - expandedPile.position.y) > height) { dismissExpandedPile(); return }
                } else {
                    val cbX = pos[0] + halfDimX - 0.3f * expandedPile.scale; val cbZ = pos[2] - halfDimZ + 0.3f * expandedPile.scale
                    if (abs(hitX - cbX) < 0.4f && abs(hitZ - cbZ) < 0.4f) { dismissExpandedPile(); return }

                    // Pagination check
                    val totalPages = ceil(expandedPile.items.size.toFloat() / 16f).toInt().coerceAtLeast(1)
                    val pZ = pos[2] + halfDimZ - 0.5f * expandedPile.scale
                    
                    // Left Arrow
                    if (expandedPile.scrollIndex > 0 && abs(hitX - (pos[0] - 1.5f * expandedPile.scale)) < 0.4f && abs(hitZ - pZ) < 0.4f) {
                        expandedPile.scrollIndex--
                        playSound(leafSoundId, 0.2f); hapticManager.tick(); return
                    }
                    // Right Arrow
                    if (expandedPile.scrollIndex < totalPages - 1 && abs(hitX - (pos[0] + 1.5f * expandedPile.scale)) < 0.4f && abs(hitZ - pZ) < 0.4f) {
                        expandedPile.scrollIndex++
                        playSound(leafSoundId, 0.2f); hapticManager.tick(); return
                    }
                    // Dots
                    val dotSpacing = 0.3f * expandedPile.scale
                    val startX = pos[0] - ((totalPages - 1) * dotSpacing) / 2f
                    for (i in 0 until totalPages) {
                        if (abs(hitX - (startX + i * dotSpacing)) < 0.15f && abs(hitZ - pZ) < 0.15f) {
                            expandedPile.scrollIndex = i
                            playSound(leafSoundId, 0.2f); hapticManager.tick(); return
                        }
                    }
                }
            }
        }
        val widgetHit = interactionManager.findIntersectingWidget(rS, rE, sceneState.widgetItems)
        if (widgetHit != null) { 
            if (camera.currentViewMode != CameraManager.ViewMode.WIDGET_FOCUS) { 
                playSound(focusSoundId, 0.4f)
                hapticManager.selection()
                camera.focusOnWidget(widgetHit.first)
                (context as? LauncherActivity)?.showResetButton(true) 
            }
            return 
        }
        val item = interactionManager.findIntersectingItem(rS, rE, sceneState.bumpItems, sceneState.piles)
        if (item != null) {
            val pile = sceneState.getPileOf(item)
            if (pile != null && pile == sceneState.recentsPile && camera.currentViewMode == CameraManager.ViewMode.BACK_WALL) {
                val t = (pile.position.z - rS[2]) / (rE[2] - rS[2]); val u = (rS[0] + t * (rE[0] - rS[0]) - (item.transform.position.x - item.transform.scale)) / (2f * item.transform.scale); val v = 1f - (rS[1] + t * (rE[1] - rS[1]) - (item.transform.position.y - item.transform.scale * 1.6f)) / (2f * item.transform.scale * 1.6f)
                if (u > 0.85f && v < 0.15f) { sceneState.recentsPile?.items?.remove(item); return }
                if (v > 0.76f && v < 0.89f) {
                    val pkg = item.appData?.appInfo?.packageName ?: ""
                    if (u < 0.25f) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else if (u < 0.5f) (context as? LauncherActivity)?.launchApp(item, LauncherActivity.WINDOWING_MODE_FULLSCREEN)
                    else if (u < 0.75f) (context as? LauncherActivity)?.launchApp(item, LauncherActivity.WINDOWING_MODE_FREEFORM)
                    else (context as? LauncherActivity)?.launchApp(item, LauncherActivity.WINDOWING_MODE_PINNED)
                    return
                }
            }
            if (item.appearance.type == BumpItem.Type.APP && item.appData?.appInfo?.packageName == context.packageName) { context.startActivity(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
            if (pile != null && !pile.isExpanded) {
                playSound(expandSoundId, 0.3f)
                hapticManager.selection()
                if (pile.isSystem && pile == sceneState.recentsPile) camera.focusOnWall(CameraManager.ViewMode.BACK_WALL, floatArrayOf(0f, 4f, 2f), floatArrayOf(0f, 4f, -10f))
                else { sceneState.piles.forEach { it.isExpanded = false }; pile.isExpanded = true; camera.focusOnFolder(pile.position.toFloatArray()) }
                (context as? LauncherActivity)?.showResetButton(true); return
            }
            if (item.appearance.type == BumpItem.Type.APP_DRAWER) {
                val apps = sceneState.allAppsList
                if (apps.isNotEmpty()) {
                    playSound(expandSoundId, 0.3f)
                    hapticManager.selection()
                    val p = item.transform.position.copy(); val dp = Pile(apps.map { BumpItem(appInfo = it, position = p.copy()) }.toMutableList(), p, name = "All Apps", isSystem = true)
                    sceneState.piles.forEach { it.isExpanded = false }; dp.isExpanded = true; sceneState.piles.add(dp); camera.focusOnFolder(p.toFloatArray()); (context as? LauncherActivity)?.showResetButton(true)
                }
                return
            }
            if (item.appearance.type == BumpItem.Type.APP || item.appearance.type == BumpItem.Type.RECENT_APP) (context as? LauncherActivity)?.launchApp(item) else if (item.appearance.type == BumpItem.Type.STICKY_NOTE) (context as? LauncherActivity)?.promptEditStickyNote(item) else if (item.appearance.type == BumpItem.Type.PHOTO_FRAME) (context as? LauncherActivity)?.promptChangePhoto(item) else if (item.appearance.type == BumpItem.Type.WEB_WIDGET) (context as? LauncherActivity)?.promptEditWebWidget(item)
        } else if (camera.currentViewMode != CameraManager.ViewMode.DEFAULT) dismissExpandedPile()
    }

    fun handleDoubleTap(x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val walls = listOf(Triple(BumpItem.Surface.BACK_WALL, floatArrayOf(0f, 4f, 2f), floatArrayOf(0f, 4f, -10f)), Triple(BumpItem.Surface.LEFT_WALL, floatArrayOf(2f, 4f, 0f), floatArrayOf(-10f, 4f, 0f)), Triple(BumpItem.Surface.RIGHT_WALL, floatArrayOf(-2f, 4f, 0f), floatArrayOf(10f, 4f, 0f)))
        var best: Triple<BumpItem.Surface, FloatArray, FloatArray>? = null; var minT = Float.MAX_VALUE
        walls.forEach { (s, cp, la) -> val t = when (s) { BumpItem.Surface.BACK_WALL -> (-9.95f - rS[2]) / (rE[2] - rS[2]); BumpItem.Surface.LEFT_WALL -> (-9.95f - rS[0]) / (rE[0] - rS[0]); BumpItem.Surface.RIGHT_WALL -> (9.95f - rS[0]) / (rE[0] - rS[0]); else -> -1f }; if (t > 0 && t < minT) { if (abs(rS[0] + t * (rE[0] - rS[0])) <= 10.1f && abs(rS[2] + t * (rE[2] - rS[2])) <= 10.1f && (rS[1] + t * (rE[1] - rS[1])) in 0f..12f) { minT = t; best = Triple(s, cp, la) } } }
        if (best != null) { camera.focusOnWall(when(best!!.first) { BumpItem.Surface.BACK_WALL -> CameraManager.ViewMode.BACK_WALL; BumpItem.Surface.LEFT_WALL -> CameraManager.ViewMode.LEFT_WALL; else -> CameraManager.ViewMode.RIGHT_WALL }, best!!.second, best!!.third); (context as? LauncherActivity)?.showResetButton(true); return }
        val tf = -rS[1] / (rE[1] - rS[1]); if (tf > 0 && abs(rS[0] + tf * (rE[0] - rS[0])) <= 10f && abs(rS[2] + tf * (rE[2] - rS[2])) <= 10f) { camera.focusOnFloor(); (context as? LauncherActivity)?.showResetButton(true) ; playSound(focusSoundId, 0.4f); hapticManager.selection(); return }
        handleSingleTap(x, y)
    }

    fun handleLongPress(x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val wHit = interactionManager.findIntersectingWidget(rS, rE, sceneState.widgetItems); val iHit = interactionManager.findIntersectingItem(rS, rE, sceneState.bumpItems, sceneState.piles)
        if (wHit != null && (iHit == null || wHit.second < 0.8f)) { sceneState.selectedWidget = wHit.first; (context as? LauncherActivity)?.showWidgetMenu(x, y, wHit.first); return }
        if (iHit != null) { val pile = sceneState.getPileOf(iHit); if (pile != null && !pile.isSystem) (context as? LauncherActivity)?.showPileMenu(x, y, pile) { breakPile(pile) } else (context as? LauncherActivity)?.showItemMenu(x, y, iHit) } else (context as? LauncherActivity)?.showDesktopMenu(x, y)
    }

    fun handleTilt(dy: Float) {
        camera.handleTilt(dy)
    }

    private fun breakPile(pile: Pile) { if (pile.isSystem) return; sceneState.piles.remove(pile); pile.items.forEach { if (!sceneState.bumpItems.contains(it)) sceneState.bumpItems.add(it); it.transform.surface = BumpItem.Surface.FLOOR; it.transform.position = it.transform.position.copy(y = 0.05f, x = it.transform.position.x + (Math.random().toFloat() - 0.5f) * 2f, z = it.transform.position.z + (Math.random().toFloat() - 0.5f) * 2f) } }
    fun resetView() { sceneState.piles.removeAll { it.isSystem && it.name == "All Apps" }; sceneState.piles.forEach { it.isExpanded = false }; camera.reset(); (context as? LauncherActivity)?.showResetButton(false) }
    fun dismissExpandedPile() { sceneState.piles.removeAll { it.isSystem && it.name == "All Apps" }; sceneState.piles.forEach { it.isExpanded = false }; camera.restorePreviousView(); (context as? LauncherActivity)?.showResetButton(camera.currentViewMode != CameraManager.ViewMode.DEFAULT) }
    fun handleZoom(sf: Float) { camera.zoomLevel = (camera.zoomLevel / sf).coerceIn(0.5f, 2.0f); if (camera.zoomLevel != 1.0f) (context as? LauncherActivity)?.showResetButton(true) }
    fun handlePan(dx: Float, dy: Float) { camera.handlePan(dx, dy); (context as? LauncherActivity)?.showResetButton(true) }
    override fun onSurfaceChanged(unused: GL10, w: Int, h: Int) { 
        surfaceWidth = w; surfaceHeight = h
        GLES20.glViewport(0, 0, w, h); interactionManager.screenWidth = w; interactionManager.screenHeight = h; 
        Matrix.perspectiveM(projectionMatrix, 0, camera.fieldOfView, w.toFloat() / h, 0.1f, 100f) 
    }
}
