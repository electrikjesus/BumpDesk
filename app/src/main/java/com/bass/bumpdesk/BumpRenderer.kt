package com.bass.bumpdesk

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bass.bumpdesk.persistence.DeskDatabase
import com.bass.bumpdesk.persistence.DeskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

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
    private lateinit var appIconBox: Box
    private lateinit var widgetBox: Box
    private lateinit var lasso: Lasso

    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var floorTextureId = -1
    private var wallTextureIds = IntArray(4) { -1 }
    private var closeBtnTextureId = -1
    private var arrowLeftTextureId = -1
    private var arrowRightTextureId = -1
    private var pileBgTextureId = -1
    private var scrollUpTextureId = -1
    private var scrollDownTextureId = -1

    private val lightPos = floatArrayOf(0f, 10f, 0f)
    private var soundPool: SoundPool? = null
    private var bumpSoundId: Int = -1

    private val db by lazy { DeskDatabase.getDatabase(context) }
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

        startPhysics()
    }

    private fun startPhysics() {
        physicsThread = PhysicsThread(sceneState, physicsEngine) {
            if (bumpSoundId != -1) soundPool?.play(bumpSoundId, 0.1f, 0.1f, 1, 0, 1.0f)
        }
        physicsThread?.start()
    }

    fun onResume() {
        if (physicsThread == null || !physicsThread!!.isAlive) {
            startPhysics()
        }
    }

    fun onPause() {
        physicsThread?.stopPhysics()
        physicsThread = null
        saveState()
    }

    private fun saveState() {
        repositoryScope.launch {
            val items = sceneState.bumpItems.map { item ->
                DeskItem(
                    packageName = item.appInfo?.packageName ?: "",
                    posX = item.position[0],
                    posY = item.position[1],
                    posZ = item.position[2],
                    surface = item.surface.name,
                    isPinned = item.isPinned,
                    scale = item.scale
                )
            }
            db.deskItemDao().deleteAll()
            db.deskItemDao().insertAll(items)
        }
    }

    fun loadSavedState(allApps: List<AppInfo>) {
        repositoryScope.launch {
            val savedItems = db.deskItemDao().getAll()
            if (savedItems.isNotEmpty()) {
                val items = savedItems.mapNotNull { saved ->
                    val appInfo = allApps.find { it.packageName == saved.packageName } ?: return@mapNotNull null
                    BumpItem(
                        appInfo = appInfo,
                        position = floatArrayOf(saved.posX, saved.posY, saved.posZ),
                        surface = BumpItem.Surface.valueOf(saved.surface),
                        isPinned = saved.isPinned,
                        scale = saved.scale
                    )
                }
                sceneState.bumpItems.clear()
                sceneState.bumpItems.addAll(items)
            }
        }
    }

    fun setAllAppsList(apps: List<AppInfo>) {
        sceneState.allAppsList.clear()
        sceneState.allAppsList.addAll(apps)
        
        if (sceneState.appDrawerItem == null) {
            val pos = floatArrayOf(6f, 0.05f, 6f)
            sceneState.appDrawerItem = BumpItem(type = BumpItem.Type.APP_DRAWER, position = pos, scale = 0.8f)
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
        val pos = hit?.second ?: floatArrayOf(0f, 0.05f, 0f)
        val surface = hit?.first ?: BumpItem.Surface.FLOOR
        sceneState.bumpItems.add(BumpItem(type = BumpItem.Type.STICKY_NOTE, text = text, position = pos, surface = surface, color = floatArrayOf(1f, 1f, 0.6f, 1f)))
    }

    fun addPhotoFrame(uri: String, x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        val pos = hit?.second ?: floatArrayOf(0f, 0.05f, 0f)
        val surface = hit?.first ?: BumpItem.Surface.FLOOR
        sceneState.bumpItems.add(BumpItem(type = BumpItem.Type.PHOTO_FRAME, text = uri, position = pos, surface = surface, scale = 1.5f))
    }

    fun addWebWidget(url: String, x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        val pos = hit?.second ?: floatArrayOf(0f, 0.05f, 0f)
        val surface = hit?.first ?: BumpItem.Surface.FLOOR
        sceneState.bumpItems.add(BumpItem(type = BumpItem.Type.WEB_WIDGET, text = url, position = pos, surface = surface, scale = 2.0f))
    }

    fun performSearch(query: String) { searchQuery = query.lowercase() }

    fun addWidgetAt(appWidgetId: Int, hostView: AppWidgetHostView, x: Float, y: Float) {
        sceneState.widgetViews[appWidgetId] = hostView
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        val widget = if (hit != null) {
            WidgetItem(appWidgetId = appWidgetId, position = hit.second, surface = hit.first).apply {
                when (surface) {
                    BumpItem.Surface.BACK_WALL -> position[2] = -9.9f
                    BumpItem.Surface.LEFT_WALL -> position[0] = -9.9f
                    BumpItem.Surface.RIGHT_WALL -> position[0] = 9.9f
                    BumpItem.Surface.FLOOR -> position[1] = 0.1f
                }
            }
        } else { WidgetItem(appWidgetId = appWidgetId, position = floatArrayOf(0f, 3f, -9.9f)) }
        sceneState.widgetItems.add(widget)
    }

    fun removeWidget(widget: WidgetItem) {
        sceneState.widgetItems.remove(widget)
        sceneState.widgetViews.remove(widget.appWidgetId)
    }

    fun togglePin(item: BumpItem) { item.isPinned = !item.isPinned }

    fun updateRecents(recents: List<AppInfo>) {
        if (sceneState.recentsPile == null) {
            val pilePos = floatArrayOf(0f, 4f, -9.4f)
            sceneState.recentsPile = Pile(mutableListOf(), pilePos, name = "Recents", layoutMode = Pile.LayoutMode.CAROUSEL, surface = BumpItem.Surface.BACK_WALL, isSystem = true)
            sceneState.piles.add(sceneState.recentsPile!!)
        }
        if (recents.isEmpty()) { sceneState.recentsPile?.items?.clear(); return }

        val recentBumpItems = recents.map { appInfo ->
            val existing = sceneState.bumpItems.find { it.appInfo?.packageName == appInfo.packageName } ?:
                           sceneState.piles.flatMap { it.items }.find { it.appInfo?.packageName == appInfo.packageName }
            val item = existing?.copy()?.apply { 
                type = BumpItem.Type.RECENT_APP
                position = sceneState.recentsPile!!.position.clone()
                scale = 1.2f
                surface = BumpItem.Surface.BACK_WALL
                textureId = -1 
            } ?: BumpItem(
                type = BumpItem.Type.RECENT_APP,
                appInfo = appInfo, 
                position = sceneState.recentsPile!!.position.clone(), 
                scale = 1.2f, 
                surface = BumpItem.Surface.BACK_WALL
            )
            
            item.textureId = TextureUtils.loadRecentTaskTexture(context, appInfo.snapshot, appInfo.icon, appInfo.label)
            item
        }
        sceneState.recentsPile!!.items.clear()
        sceneState.recentsPile!!.items.addAll(recentBumpItems)
    }

    fun reloadTheme() {
        glSurfaceView?.queueEvent { 
            textureManager.clearCache()
            sceneState.bumpItems.forEach { it.textureId = -1 }
            sceneState.piles.forEach { pile -> pile.items.forEach { it.textureId = -1 } }
            sceneState.widgetItems.forEach { it.textureId = -1 }
            
            // Reload static UI textures that were lost in clearCache
            closeBtnTextureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("X", 64, 64))
            arrowLeftTextureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap(" < ", 64, 64))
            arrowRightTextureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap(" > ", 64, 64))
            
            loadThemeTextures() 
        }
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.02f, 0.02f, 0.02f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        shader = DefaultShader(); roomRenderer = RoomRenderer(shader); overlayRenderer = OverlayRenderer(shader); appIconBox = Box(shader); widgetBox = Box(shader); lasso = Lasso()
        
        // Ensure all items reload textures on new surface
        sceneState.bumpItems.forEach { it.textureId = -1 }
        sceneState.piles.forEach { pile -> pile.items.forEach { it.textureId = -1 } }
        sceneState.widgetItems.forEach { it.textureId = -1 }

        closeBtnTextureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("X", 64, 64))
        arrowLeftTextureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap(" < ", 64, 64))
        arrowRightTextureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap(" > ", 64, 64))
        loadThemeTextures()
    }

    private fun loadThemeTextures() {
        floorTextureId = ThemeManager.getFloorTexture(context, textureManager)
        wallTextureIds = ThemeManager.getWallTextures(context, textureManager)
        pileBgTextureId = ThemeManager.getPileBackgroundTexture(context, textureManager)
        scrollUpTextureId = textureManager.loadTextureFromAsset("BumpTop/${ThemeManager.currentThemeName}/widgets/scrollUp.png")
        scrollDownTextureId = textureManager.loadTextureFromAsset("BumpTop/${ThemeManager.currentThemeName}/widgets/scrollDown.png")
    }

    override fun onDrawFrame(unused: GL10) {
        frameCount++
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        camera.update(); camera.setViewMatrix(viewMatrix)
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.invertM(interactionManager.invertedVPMatrix, 0, vPMatrix, 0)
        
        roomRenderer.draw(vPMatrix, floorTextureId, wallTextureIds, lightPos)
        
        val activePile = sceneState.piles.find { it.isExpanded }
        if (camera.currentViewMode == CameraManager.ViewMode.FOLDER_EXPANDED && activePile != null) {
            if (activePile.nameTextureId == -1) activePile.nameTextureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap(activePile.name, 512, 64))
            overlayRenderer.drawFolderUI(vPMatrix, activePile, closeBtnTextureId, activePile.nameTextureId, lightPos)
            if (activePile.layoutMode == Pile.LayoutMode.GRID) {
                overlayRenderer.drawGridScrollButtons(vPMatrix, activePile, scrollUpTextureId, scrollDownTextureId, lightPos)
            }
        }
        sceneState.recentsPile?.let { overlayRenderer.drawRecentsOverlay(vPMatrix, it, arrowLeftTextureId, arrowRightTextureId, lightPos) }
        
        // Task: Live widget updates - increase frequency and optimize
        sceneState.widgetItems.forEach { widget ->
            if (widget.textureId <= 0 || (frameCount % 30 == 0)) updateWidgetTexture(widget)
            drawWidget(widget)
        }
        sceneState.bumpItems.forEach { item -> 
            ensureItemTexture(item)
            if (item.type == BumpItem.Type.WEB_WIDGET && frameCount % 60 == 0) updateWebTexture(item)
            drawItem(item) 
        }
        
        sceneState.piles.forEach { pile ->
            val isCarousel = pile.layoutMode == Pile.LayoutMode.CAROUSEL && !pile.isExpanded
            val widthLimit = 6f * pile.scale
            pile.items.forEach { item ->
                if (isCarousel && Math.abs(item.position[0] - pile.position[0]) > widthLimit - 0.5f) return@forEach
                ensureItemTexture(item); drawItem(item)
            }
        }
        if (interactionManager.lassoPoints.isNotEmpty()) lasso.draw(vPMatrix, interactionManager.lassoPoints)
    }

    private fun ensureItemTexture(item: BumpItem) {
        if (item.textureId != -1) return
        when (item.type) {
            BumpItem.Type.APP -> {
                item.appInfo?.let { app ->
                    if (app.icon != null) {
                        val iconBitmap = TextureUtils.getBitmapFromDrawable(app.icon)
                        val labelBitmap = TextRenderer.createTextBitmap(app.label, 256, 64)
                        val combined = TextureUtils.getCombinedBitmap(context, iconBitmap, labelBitmap, false)
                        item.textureId = textureManager.loadTextureFromBitmap(combined)
                        combined.recycle()
                    }
                }
            }
            BumpItem.Type.STICKY_NOTE -> {
                val bitmap = TextRenderer.createTextBitmap(item.text, 512, 512)
                item.textureId = textureManager.loadTextureFromBitmap(bitmap); bitmap.recycle()
            }
            BumpItem.Type.PHOTO_FRAME -> {
                if (item.text.isNotEmpty()) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(Uri.parse(item.text))
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) { item.textureId = textureManager.loadTextureFromBitmap(bitmap); bitmap.recycle() }
                    } catch (e: Exception) { item.textureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("Photo missing", 256, 256)) }
                } else { item.textureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("Pick Photo", 256, 256)) }
            }
            BumpItem.Type.WEB_WIDGET -> { if (item.text.isNotEmpty()) updateWebTexture(item) else item.textureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("Set URL", 256, 256)) }
            BumpItem.Type.RECENT_APP -> {
                item.appInfo?.let { app ->
                    item.textureId = TextureUtils.loadRecentTaskTexture(context, app.snapshot, app.icon, app.label)
                }
            }
            BumpItem.Type.APP_DRAWER -> {
                val labelBitmap = TextRenderer.createTextBitmap("All Apps", 256, 64)
                val icon = context.getDrawable(context.applicationInfo.icon) ?: context.getDrawable(android.R.drawable.sym_def_app_icon)
                icon?.let {
                    val iconBitmap = TextureUtils.getBitmapFromDrawable(it)
                    val combined = TextureUtils.getCombinedBitmap(context, iconBitmap, labelBitmap, false)
                    item.textureId = textureManager.loadTextureFromBitmap(combined)
                    combined.recycle()
                }
            }
        }
    }

    private fun updateWebTexture(item: BumpItem) {
        val webView = sceneState.webViews[item.hashCode()] ?: run {
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true; webViewClient = WebViewClient()
                layout(0, 0, 1024, 1024); loadUrl(item.text)
            }
            sceneState.webViews[item.hashCode()] = wv; wv
        }
        webView.post {
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            webView.draw(Canvas(bitmap))
            glSurfaceView?.queueEvent {
                if (item.textureId == -1) {
                    item.textureId = textureManager.loadTextureFromBitmap(bitmap)
                } else {
                    textureManager.updateTextureFromBitmap(item.textureId, bitmap)
                }
                bitmap.recycle()
            }
        }
    }

    private fun updateWidgetTexture(widget: WidgetItem) {
        val view = sceneState.widgetViews[widget.appWidgetId] ?: return
        view.post {
            val w = view.width.coerceAtLeast(1024)
            val h = view.height.coerceAtLeast(1024)
            if (view.width <= 0 || view.height <= 0) {
                val spec = View.MeasureSpec.makeMeasureSpec(1024, View.MeasureSpec.EXACTLY)
                view.measure(spec, spec); view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            }
            val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            glSurfaceView?.queueEvent {
                if (widget.textureId == -1) {
                    widget.textureId = textureManager.loadTextureFromBitmap(bitmap)
                } else {
                    textureManager.updateTextureFromBitmap(widget.textureId, bitmap)
                }
                bitmap.recycle()
            }
        }
    }

    private fun drawWidget(widget: WidgetItem) {
        Matrix.setIdentityM(modelMatrix, 0); Matrix.translateM(modelMatrix, 0, widget.position[0], widget.position[1], widget.position[2])
        when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> { Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f); Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f) }
            BumpItem.Surface.LEFT_WALL -> { Matrix.rotateM(modelMatrix, 0, 90f, 0f, 1f, 0f); Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f) }
            BumpItem.Surface.RIGHT_WALL -> { Matrix.rotateM(modelMatrix, 0, -90f, 0f, 1f, 0f); Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f) }
            BumpItem.Surface.FLOOR -> {}
        }
        Matrix.scaleM(modelMatrix, 0, widget.size[0], 1f, widget.size[1])
        widgetBox.draw(vPMatrix, modelMatrix, widget.textureId, floatArrayOf(1f, 1f, 1f, 1.0f))
    }

    private fun drawItem(item: BumpItem) {
        Matrix.setIdentityM(modelMatrix, 0); Matrix.translateM(modelMatrix, 0, item.position[0], item.position[1], item.position[2])
        val pile = sceneState.getPileOf(item); val surfaceToUse = if (pile?.isExpanded == true) BumpItem.Surface.FLOOR else item.surface
        when (surfaceToUse) {
            BumpItem.Surface.BACK_WALL -> { Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f); Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f) }
            BumpItem.Surface.LEFT_WALL -> { Matrix.rotateM(modelMatrix, 0, 90f, 0f, 1f, 0f); Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f) }
            BumpItem.Surface.RIGHT_WALL -> { Matrix.rotateM(modelMatrix, 0, -90f, 0f, 1f, 0f); Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f) }
            else -> {}
        }
        val heightMult = when (item.type) { 
            BumpItem.Type.APP -> if (pile?.layoutMode == Pile.LayoutMode.CAROUSEL) 1.6f else 1.25f
            BumpItem.Type.RECENT_APP -> 1.6f
            BumpItem.Type.APP_DRAWER -> 1.25f
            else -> 1.0f 
        }
        Matrix.scaleM(modelMatrix, 0, item.scale, 1f, item.scale * heightMult)
        
        var color = if (item.isPinned) floatArrayOf(0.8f, 0.8f, 1.0f, 1.0f) else item.color
        if (item == sceneState.selectedItem) {
            val selectionColor = ThemeManager.getSelectionColor()
            color = floatArrayOf(selectionColor[0], selectionColor[1], selectionColor[2], 1.0f)
        }
        if (searchQuery.isNotEmpty()) {
            val label = when (item.type) { BumpItem.Type.APP, BumpItem.Type.RECENT_APP -> item.appInfo?.label ?: ""; BumpItem.Type.STICKY_NOTE -> item.text; BumpItem.Type.WEB_WIDGET -> item.text; else -> "" }
            if (label.lowercase().contains(searchQuery)) color = ThemeManager.getFreshnessColor() else color = floatArrayOf(color[0]*0.3f, color[1]*0.3f, color[2]*0.3f, 0.5f)
        }
        appIconBox.draw(vPMatrix, modelMatrix, item.textureId, color)
    }

    fun handleTouchDown(x: Float, y: Float) = interactionManager.handleTouchDown(x, y, sceneState)
    fun handleTouchMove(x: Float, y: Float, pointerCount: Int) = interactionManager.handleTouchMove(x, y, sceneState, pointerCount)
    fun handleTouchUp() = interactionManager.handleTouchUp(sceneState) { captured -> (context as? LauncherActivity)?.showLassoMenu(interactionManager.lastTouchX, interactionManager.lastTouchY, captured) }

    fun gridSelectedItems(items: List<BumpItem>, mode: GridLayout) {
        if (items.isEmpty()) return
        val count = items.size
        val spacing = 1.5f
        val startX = items.map { it.position[0] }.average().toFloat()
        val startZ = items.map { it.position[2] }.average().toFloat()
        
        when (mode) {
            GridLayout.GRID -> {
                val side = Math.ceil(Math.sqrt(count.toDouble())).toInt()
                val offset = (side * spacing) / 2f
                items.forEachIndexed { i, item ->
                    val row = i / side
                    val col = i % side
                    item.position[0] = (startX - offset) + col * spacing
                    item.position[2] = (startZ - offset) + row * spacing
                    item.position[1] = 0.05f
                    item.surface = BumpItem.Surface.FLOOR
                    item.velocity[0] = 0f; item.velocity[1] = 0f; item.velocity[2] = 0f
                }
            }
            GridLayout.ROW -> {
                val offset = (count * spacing) / 2f
                items.forEachIndexed { i, item ->
                    item.position[0] = (startX - offset) + i * spacing
                    item.position[2] = startZ
                    item.position[1] = 0.05f
                    item.surface = BumpItem.Surface.FLOOR
                    item.velocity[0] = 0f; item.velocity[1] = 0f; item.velocity[2] = 0f
                }
            }
            GridLayout.COLUMN -> {
                val offset = (count * spacing) / 2f
                items.forEachIndexed { i, item ->
                    item.position[0] = startX
                    item.position[2] = (startZ - offset) + i * spacing
                    item.position[1] = 0.05f
                    item.surface = BumpItem.Surface.FLOOR
                    item.velocity[0] = 0f; item.velocity[1] = 0f; item.velocity[2] = 0f
                }
            }
        }
    }

    private fun createPileFromCaptured(capturedItems: List<BumpItem>) {
        sceneState.piles.forEach { pile -> pile.items.removeAll(capturedItems) }
        sceneState.piles.removeAll { it.items.size < 2 && !it.isSystem }
        val pPos = floatArrayOf(capturedItems.map { it.position[0] }.average().toFloat(), 0.05f, capturedItems.map { it.position[2] }.average().toFloat())
        sceneState.piles.add(Pile(capturedItems.toMutableList(), pPos))
    }

    fun handleSingleTap(x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val expandedPile = sceneState.piles.find { it.isExpanded }
        if (expandedPile != null) {
            val tFloor = (2.98f - rS[1]) / (rE[1] - rS[1])
            if (tFloor > 0) {
                val iX = rS[0] + tFloor * (rE[0] - rS[0]); val iZ = rS[2] + tFloor * (rE[2] - rS[2]); val (halfDim, totalHalfDimZ, pos) = overlayRenderer.getConstrainedFolderUI(expandedPile)
                val uiX = pos[0]; val uiZ = pos[1]; val cbX = uiX + halfDim - 0.2f * expandedPile.scale; val cbZ = uiZ - totalHalfDimZ + 0.2f * expandedPile.scale
                if (Math.abs(iX - cbX) < 0.4f && Math.abs(iZ - cbZ) < 0.4f) { dismissExpandedPile(); return }
                if (expandedPile.layoutMode == Pile.LayoutMode.GRID) {
                    val suX = uiX - halfDim + 0.4f * expandedPile.scale
                    val suZ = uiZ - totalHalfDimZ + 0.2f * expandedPile.scale
                    if (Math.abs(iX - suX) < 0.4f && Math.abs(iZ - suZ) < 0.4f) { expandedPile.scrollIndex = (expandedPile.scrollIndex - 1).coerceAtLeast(0); return }
                    val sdX = uiX - halfDim + 1.0f * expandedPile.scale
                    if (Math.abs(iX - sdX) < 0.4f && Math.abs(iZ - suZ) < 0.4f) { expandedPile.scrollIndex++; return }
                }
            }
        }
        val widgetHit = interactionManager.findIntersectingWidget(rS, rE, sceneState.widgetItems)
        if (widgetHit != null) { interactWithWidget(widgetHit.first, rS, rE); if (camera.currentViewMode != CameraManager.ViewMode.WIDGET_FOCUS) { camera.focusOnWidget(widgetHit.first); (context as? LauncherActivity)?.showResetButton(true) }; return }
        
        val item = interactionManager.findIntersectingItem(rS, rE, sceneState.bumpItems, sceneState.piles)
        if (item != null) {
            val pile = sceneState.getPileOf(item)
            
            // Task: Hit detection for Recents task items (Close button and Action icons)
            if (pile != null && pile == sceneState.recentsPile && camera.currentViewMode == CameraManager.ViewMode.BACK_WALL) {
                val t = (pile.position[2] - rS[2]) / (rE[2] - rS[2])
                val iX = rS[0] + t * (rE[0] - rS[0])
                val iY = rS[1] + t * (rE[1] - rS[1])
                
                // Texture coordinates on the item (0..1)
                val u = (iX - (item.position[0] - item.scale)) / (2f * item.scale)
                // Height mult for recent task is 1.6f
                val v = 1f - (iY - (item.position[1] - item.scale * 1.6f)) / (2f * item.scale * 1.6f)
                
                if (u > 0.85f && v < 0.15f) { // Close button area
                    sceneState.recentsPile?.items?.remove(item)
                    return
                }
                
                if (v > 0.76f && v < 0.89f) { // Action icons row
                    if (u < 0.25f) { // App Info
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", item.appInfo?.packageName ?: "", null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } else if (u < 0.5f) { // Fullscreen
                        (context as? LauncherActivity)?.launchApp(item, LauncherActivity.WINDOWING_MODE_FULLSCREEN)
                    } else if (u < 0.75f) { // Freeform
                        (context as? LauncherActivity)?.launchApp(item, LauncherActivity.WINDOWING_MODE_FREEFORM)
                    } else { // Pinned
                        (context as? LauncherActivity)?.launchApp(item, LauncherActivity.WINDOWING_MODE_PINNED)
                    }
                    return
                }
            }

            if (item.type == BumpItem.Type.APP && item.appInfo?.packageName == context.packageName) { context.startActivity(Intent(context, SettingsActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }); return }

            if (pile != null && !pile.isExpanded) {
                if (pile.isSystem && pile == sceneState.recentsPile) { camera.focusOnWall(CameraManager.ViewMode.BACK_WALL, floatArrayOf(0f, 4f, 2f), floatArrayOf(0f, 4f, -10f)); (context as? LauncherActivity)?.showResetButton(true) }
                else { sceneState.piles.forEach { it.isExpanded = false }; pile.isExpanded = true; camera.focusOnFolder(pile.position); (context as? LauncherActivity)?.showResetButton(true) }
                return
            }
            
            // Task: Handle App Drawer item tap
            if (item.type == BumpItem.Type.APP_DRAWER) {
                val apps = sceneState.allAppsList
                if (apps.isNotEmpty()) {
                    val pilePos = item.position.clone()
                    val drawerPile = Pile(
                        apps.map { BumpItem(appInfo = it, position = pilePos.clone()) }.toMutableList(),
                        pilePos, 
                        name = "All Apps", 
                        isSystem = true
                    )
                    sceneState.piles.forEach { it.isExpanded = false }
                    drawerPile.isExpanded = true
                    sceneState.piles.add(drawerPile)
                    camera.focusOnFolder(pilePos)
                    (context as? LauncherActivity)?.showResetButton(true)
                }
                return
            }

            if (item.type == BumpItem.Type.APP || item.type == BumpItem.Type.RECENT_APP) (context as? LauncherActivity)?.launchApp(item) else if (item.type == BumpItem.Type.STICKY_NOTE) (context as? LauncherActivity)?.promptEditStickyNote(item) else if (item.type == BumpItem.Type.PHOTO_FRAME) (context as? LauncherActivity)?.promptChangePhoto(item) else if (item.type == BumpItem.Type.WEB_WIDGET) (context as? LauncherActivity)?.promptEditWebWidget(item)
        } else if (camera.currentViewMode != CameraManager.ViewMode.DEFAULT) { dismissExpandedPile() }
    }

    private fun interactWithWidget(widget: WidgetItem, rS: FloatArray, rE: FloatArray) {
        val view = sceneState.widgetViews[widget.appWidgetId] ?: return
        val t = when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> (widget.position[2] - rS[2]) / (rE[2] - rS[2]); BumpItem.Surface.LEFT_WALL -> (widget.position[0] - rS[0]) / (rE[0] - rS[0]); BumpItem.Surface.RIGHT_WALL -> (widget.position[0] - rS[0]) / (rE[0] - rS[0]); else -> return
        }
        val iX = rS[0] + t * (rE[0] - rS[0]); val iY = rS[1] + t * (rE[1] - rS[1]); val iZ = rS[2] + t * (rE[2] - rS[2])
        val (u, v) = when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> (iX - (widget.position[0] - widget.size[0])) / (2f * widget.size[0]) to 1f - (iY - (widget.position[1] - widget.size[1])) / (2f * widget.size[1]); BumpItem.Surface.LEFT_WALL -> (iZ - (widget.position[2] - widget.size[0])) / (2f * widget.size[0]) to 1f - (iY - (widget.position[1] - widget.size[1])) / (2f * widget.size[1]); BumpItem.Surface.RIGHT_WALL -> 1f - (iZ - (widget.position[2] - widget.size[0])) / (2f * widget.size[0]) to 1f - (iY - (widget.position[1] - widget.size[1])) / (2f * widget.size[1]); else -> return
        }
        view.post { val downTime = android.os.SystemClock.uptimeMillis(); view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, u * view.width, v * view.height, 0)); view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_UP, u * view.width, v * view.height, 0)) }
    }

    fun handleDoubleTap(x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val walls = listOf(Triple(BumpItem.Surface.BACK_WALL, floatArrayOf(0f, 4f, 2f), floatArrayOf(0f, 4f, -10f)), Triple(BumpItem.Surface.LEFT_WALL, floatArrayOf(2f, 4f, 0f), floatArrayOf(-10f, 4f, 0f)), Triple(BumpItem.Surface.RIGHT_WALL, floatArrayOf(-2f, 4f, 0f), floatArrayOf(10f, 4f, 0f)))
        var bestWall: Triple<BumpItem.Surface, FloatArray, FloatArray>? = null; var minT = Float.MAX_VALUE
        walls.forEach { (surface, camPos, lookAt) -> val t = when (surface) { BumpItem.Surface.BACK_WALL -> (-9.95f - rS[2]) / (rE[2] - rS[2]); BumpItem.Surface.LEFT_WALL -> (-9.95f - rS[0]) / (rE[0] - rS[0]); BumpItem.Surface.RIGHT_WALL -> (9.95f - rS[0]) / (rE[0] - rS[0]); else -> -1f }; if (t > 0 && t < minT) { val hitX = rS[0] + t * (rE[0] - rS[0]); val hitY = rS[1] + t * (rE[1] - rS[1]); val hitZ = rS[2] + t * (rE[2] - rS[2]); if (Math.abs(hitX) <= 10.1f && Math.abs(hitZ) <= 10.1f && hitY >= 0f && hitY <= 12f) { minT = t; bestWall = Triple(surface, camPos, lookAt) } } }
        if (bestWall != null) { camera.focusOnWall(when(bestWall!!.first) { BumpItem.Surface.BACK_WALL -> CameraManager.ViewMode.BACK_WALL; BumpItem.Surface.LEFT_WALL -> CameraManager.ViewMode.LEFT_WALL; BumpItem.Surface.RIGHT_WALL -> CameraManager.ViewMode.RIGHT_WALL; else -> CameraManager.ViewMode.DEFAULT }, bestWall!!.second, bestWall!!.third); (context as? LauncherActivity)?.showResetButton(true); return }
        val tFloor = -rS[1] / (rE[1] - rS[1]); if (tFloor > 0) { val iX = rS[0] + tFloor * (rE[0] - rS[0]); val iZ = rS[2] + tFloor * (rE[2] - rS[2]); if (Math.abs(iX) <= 10f && Math.abs(iZ) <= 10f) { camera.focusOnFloor(); (context as? LauncherActivity)?.showResetButton(true); return } }
        handleSingleTap(x, y)
    }

    fun handleLongPress(x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val widgetHit = interactionManager.findIntersectingWidget(rS, rE, sceneState.widgetItems)
        val itemHit = interactionManager.findIntersectingItem(rS, rE, sceneState.bumpItems, sceneState.piles)
        if (widgetHit != null && (itemHit == null || widgetHit.second < 0.8f)) { sceneState.selectedWidget = widgetHit.first; (context as? LauncherActivity)?.showWidgetMenu(x, y, widgetHit.first); return }
        if (itemHit != null) { val pile = sceneState.getPileOf(itemHit); if (pile != null && pile.isSystem == false) (context as? LauncherActivity)?.showPileMenu(x, y, pile) { breakPile(pile) } else (context as? LauncherActivity)?.showItemMenu(x, y, itemHit) } else (context as? LauncherActivity)?.showDesktopMenu(x, y)
    }

    private fun breakPile(pile: Pile) { if (pile.isSystem) return; sceneState.piles.remove(pile); pile.items.forEach { item -> if (!sceneState.bumpItems.contains(item)) sceneState.bumpItems.add(item); item.surface = BumpItem.Surface.FLOOR; item.position[1] = 0.05f; item.position[0] += (Math.random().toFloat() - 0.5f) * 2f; item.position[2] += (Math.random().toFloat() - 0.5f) * 2f } }
    
    fun resetView() { 
        sceneState.piles.removeAll { it.isSystem && it.name == "All Apps" }
        sceneState.piles.forEach { it.isExpanded = false }
        camera.reset()
        (context as? LauncherActivity)?.showResetButton(false) 
    }

    fun dismissExpandedPile() {
        sceneState.piles.removeAll { it.isSystem && it.name == "All Apps" }
        sceneState.piles.forEach { it.isExpanded = false }
        camera.restorePreviousView()
        (context as? LauncherActivity)?.showResetButton(camera.currentViewMode != CameraManager.ViewMode.DEFAULT)
    }

    fun handleZoom(scaleFactor: Float) { camera.zoomLevel = (camera.zoomLevel / scaleFactor).coerceIn(0.5f, 2.0f); if (camera.zoomLevel != 1.0f) (context as? LauncherActivity)?.showResetButton(true) }
    fun handlePan(dx: Float, dy: Float) { camera.targetLookAt[0] += dx * 0.01f; camera.targetLookAt[2] += dy * 0.01f; (context as? LauncherActivity)?.showResetButton(true) }
    override fun onSurfaceChanged(unused: GL10, w: Int, h: Int) { GLES20.glViewport(0, 0, w, h); interactionManager.screenWidth = w; interactionManager.screenHeight = h; Matrix.perspectiveM(projectionMatrix, 0, 60f, w.toFloat() / h, 0.1f, 100f) }
}
