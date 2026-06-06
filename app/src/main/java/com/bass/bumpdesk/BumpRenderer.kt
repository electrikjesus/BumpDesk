package com.bass.bumpdesk

import android.appwidget.AppWidgetManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.bass.bumpdesk.persistence.DeskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.content.Intent
import android.provider.Settings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

class BumpRenderer(private val context: Context) : GLSurfaceView.Renderer {

    val sceneState = SceneState()
    val camera = CameraManager()
    val interactionManager = InteractionManager(context, camera)
    private val physicsEngine = PhysicsEngine()
    private var physicsThread: PhysicsThread? = null
    val textureManager = TextureManager(context)
    val hapticManager = HapticManager(context)
    
    private var shader: DefaultShader? = null
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
    private val saveMutex = Mutex()
    @Volatile
    private var persistenceReady = false
    
    private var frameCount = 0
    var glSurfaceView: GLSurfaceView? = null
    private var searchQuery = ""
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var sessionProfileApplied = false

    var ROOM_SIZE = 30f
    var ROOM_HEIGHT = 30f
    var floorHalfWidth = 30f
    var floorHalfDepth = 30f
    var isFlatFloorMode = false

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

    fun onDestroy() {
        physicsThread?.stopPhysics()
        physicsThread = null
        soundPool?.release()
        soundPool = null

        glSurfaceView?.queueEvent {
            textureManager.destroy()
        }
    }

    fun updateSettings() {
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        ScreenMetrics.applyFirstLaunchDefaults(context, prefs)
        val displayProfile = ScreenMetrics.from(context)
        interactionManager.updateTouchMetrics(context)
        physicsEngine.friction = prefs.getInt("physics_friction", 94) / 100f
        physicsEngine.restitution = prefs.getInt("physics_bounciness", 25) / 100f
        physicsEngine.gravity = prefs.getInt("physics_gravity", 10) / 1000f
        
        val scalePref = prefs.getInt("layout_item_scale", 50) / 100f
        physicsEngine.defaultScale = scalePref + 0.2f
        physicsEngine.gridSpacingBase = (prefs.getInt("layout_grid_spacing", 60) / 100f) * 2.0f
        val folderGroupScale = (scalePref + 0.5f).coerceIn(0.5f, 2.5f)
        
        sceneState.withWriteLock {
            sceneState.bumpItems.forEach { item ->
                if (item.appearance.type != BumpItem.Type.APP_DRAWER) {
                    item.transform.scale = physicsEngine.defaultScale
                }
            }
            sceneState.piles.forEach { p ->
                if (!p.isRecentsPile()) {
                    p.scale = folderGroupScale
                }
                p.items.forEach { it.transform.scale = physicsEngine.defaultScale }
            }
            sceneState.bumpItems
                .filter { it.appearance.type == BumpItem.Type.APP_DRAWER }
                .forEach { it.transform.scale = folderGroupScale }
            sceneState.appDrawerItem?.transform?.scale = folderGroupScale
        }
        
        val showAppDrawer = prefs.getBoolean("show_app_drawer_icon", true)
        val hasAppDrawer = sceneState.bumpItems.any { it.appearance.type == BumpItem.Type.APP_DRAWER } ||
                           sceneState.piles.any { p -> p.items.any { it.appearance.type == BumpItem.Type.APP_DRAWER } }
        
        if (showAppDrawer && !hasAppDrawer) {
            val folderGroupScale = (scalePref + 0.5f).coerceIn(0.5f, 2.5f)
            sceneState.withWriteLock {
                sceneState.appDrawerItem = BumpItem(
                    type = BumpItem.Type.APP_DRAWER,
                    position = Vector3(6f, 0.05f, 6f),
                    scale = folderGroupScale,
                )
                sceneState.bumpItems.add(sceneState.appDrawerItem!!)
            }
        } else if (!showAppDrawer && hasAppDrawer) {
            sceneState.withWriteLock {
                sceneState.bumpItems.removeAll { it.appearance.type == BumpItem.Type.APP_DRAWER }
                sceneState.piles.forEach { it.items.removeAll { item -> item.appearance.type == BumpItem.Type.APP_DRAWER } }
                sceneState.appDrawerItem = null
            }
        }
        
        physicsEngine.isInfiniteMode = prefs.getBoolean("infinite_desktop_mode", false)
        interactionManager.isInfiniteMode = physicsEngine.isInfiniteMode

        isFlatFloorMode = prefs.getBoolean(FlatFloorMode.PREF_KEY, false) && !physicsEngine.isInfiniteMode
        physicsEngine.isFlatFloorMode = isFlatFloorMode
        interactionManager.isFlatFloorMode = isFlatFloorMode
        camera.isFlatFloorMode = isFlatFloorMode

        ROOM_SIZE = prefs.getInt("room_size_scale", 30).toFloat()
        ROOM_HEIGHT = ROOM_SIZE
        floorHalfWidth = ROOM_SIZE
        floorHalfDepth = ROOM_SIZE

        if (isFlatFloorMode) {
            applyFlatFloorBounds(displayProfile)
        }

        syncWallpaperFloorCrop()
        interactionManager.roomHeight = ROOM_HEIGHT
        interactionManager.floorHalfX = floorHalfWidth
        interactionManager.floorHalfZ = floorHalfDepth
        
        physicsEngine.roomSize = if (isFlatFloorMode) floorHalfWidth else ROOM_SIZE
        physicsEngine.roomHeight = ROOM_HEIGHT
        physicsEngine.floorHalfX = floorHalfWidth
        physicsEngine.floorHalfZ = floorHalfDepth

        camera.isInfiniteMode = physicsEngine.isInfiniteMode
        if (isFlatFloorMode) {
            camera.MAX_X = floorHalfWidth - 1f
            camera.MAX_Y = ROOM_HEIGHT - 1f
            camera.MAX_Z = floorHalfDepth - 1f
            camera.MIN_X = -floorHalfWidth + 1f
            camera.MIN_Z = -floorHalfDepth + 1f
        } else {
            camera.MAX_X = ROOM_SIZE - 1f
            camera.MAX_Y = ROOM_HEIGHT - 1f
            camera.MAX_Z = ROOM_SIZE - 1f
            camera.MIN_X = -ROOM_SIZE + 1f
            camera.MIN_Z = -ROOM_SIZE + 1f
        }
        
        if (prefs.getBoolean("reset_camera_trigger", false)) {
            camera.resetToAbsoluteDefaults()
            prefs.edit().remove("reset_camera_trigger").apply()
            sessionProfileApplied = true
            CameraDiagnostics.log(camera, "updateSettings", "source=resetCameraTrigger")
        } else if (isFlatFloorMode) {
            val aspect = displayAspectRatio()
            val bounds = FlatFloorMode.computeFloorBounds(
                FlatFloorMode.DEFAULT_EYE_Y,
                FlatFloorMode.DEFAULT_EYE_Z,
                FlatFloorMode.DEFAULT_FOV,
                aspect,
                FlatFloorMode.DEFAULT_ZOOM,
            )
            camera.applyFlatFloorDefaults(bounds, aspect)
            camera.syncSavedViewFromCurrent()
            sessionProfileApplied = true
            (context as? LauncherActivity)?.showResetButton(false)
            CameraDiagnostics.log(camera, "updateSettings", "source=flatFloorDefaults")
        } else if (leavingFlatFloorCamera()) {
            transitionCameraOutOfFlatFloor(displayProfile, prefs, "updateSettings")
            sessionProfileApplied = true
        } else if (physicsEngine.isInfiniteMode) {
            applyInfiniteDesktopCamera(displayProfile, "updateSettings")
            sessionProfileApplied = true
        } else if (prefs.contains("cam_def_pos_x")) {
            camera.customDefaultPos[0] = prefs.getFloat("cam_def_pos_x", camera.ABSOLUTE_DEFAULT_POS[0])
            camera.customDefaultPos[1] = prefs.getFloat("cam_def_pos_y", camera.ABSOLUTE_DEFAULT_POS[1])
            camera.customDefaultPos[2] = prefs.getFloat("cam_def_pos_z", camera.ABSOLUTE_DEFAULT_POS[2])
            camera.customDefaultLookAt[0] = prefs.getFloat("cam_def_lat_x", camera.ABSOLUTE_DEFAULT_LOOKAT[0])
            camera.customDefaultLookAt[1] = prefs.getFloat("cam_def_lat_y", camera.ABSOLUTE_DEFAULT_LOOKAT[1])
            camera.customDefaultLookAt[2] = prefs.getFloat("cam_def_lat_z", camera.ABSOLUTE_DEFAULT_LOOKAT[2])
            if (isFlatFloorStyleSavedCamera(camera.customDefaultPos, camera.customDefaultLookAt)) {
                BumpDeskLog.w(
                    BumpDeskLog.Tag.CAMERA,
                    "updateSettings",
                    "ignoring flat-floor saved camera in room mode; clearing stale cam_def prefs",
                )
                clearSavedCustomCameraPrefs(prefs)
                camera.customDefaultPos = displayProfile.defaultCameraPos.clone()
                camera.customDefaultLookAt = displayProfile.defaultCameraLookAt.clone()
                camera.customDefaultZoomLevel = displayProfile.defaultZoomLevel
                camera.baseFieldOfView = displayProfile.defaultFieldOfView
                applyOrientationProfile(displayProfile, "updateSettings")
                prefs.edit()
                    .putString(ScreenMetrics.PREFS_LAST_ORIENTATION, displayProfile.orientationKey)
                    .apply()
                sessionProfileApplied = true
            } else if (!sessionProfileApplied) {
                camera.reset()
                sessionProfileApplied = true
                CameraDiagnostics.log(
                    camera,
                    "updateSettings",
                    "source=savedCustomCamera latX=${camera.customDefaultLookAt[0]} posX=${camera.customDefaultPos[0]}",
                )
            }
        } else {
            camera.customDefaultPos = displayProfile.defaultCameraPos.clone()
            camera.customDefaultLookAt = displayProfile.defaultCameraLookAt.clone()
            camera.customDefaultZoomLevel = displayProfile.defaultZoomLevel
            camera.baseFieldOfView = displayProfile.defaultFieldOfView
            if (!sessionProfileApplied && camera.currentViewMode == CameraManager.ViewMode.DEFAULT) {
                applyOrientationProfile(displayProfile, "updateSettings")
                prefs.edit()
                    .putString(ScreenMetrics.PREFS_LAST_ORIENTATION, displayProfile.orientationKey)
                    .apply()
                sessionProfileApplied = true
            } else {
                CameraDiagnostics.log(
                    camera,
                    "updateSettings",
                    "source=displayProfile orientation=${displayProfile.orientationKey} defaultsOnly=true"
                )
            }
        }

        configureRecentsPileForCurrentMode()
        sceneState.recentsPile?.reconcilePinnedOpenState()
        AllAppsDrawer.reconcile(sceneState, camera.currentViewMode)
        constrainWidgetsToActiveBounds(saveIfChanged = true)
        glSurfaceView?.requestRender()
    }

    private fun activeWidgetBounds(): WidgetPlacement.RoomBounds {
        val boundX = if (isFlatFloorMode) floorHalfWidth else ROOM_SIZE
        val boundZ = if (isFlatFloorMode) floorHalfDepth else ROOM_SIZE
        val roomSize = if (isFlatFloorMode) floorHalfWidth else ROOM_SIZE
        return WidgetPlacement.boundsFrom(
            boundX = boundX,
            boundZ = boundZ,
            roomSize = roomSize,
            roomHeight = ROOM_HEIGHT,
            isFlatFloorMode = isFlatFloorMode,
            isInfiniteMode = physicsEngine.isInfiniteMode,
        )
    }

    fun constrainWidgetsToActiveBounds(saveIfChanged: Boolean = false) {
        val changed = sceneState.withWriteLockResult {
            WidgetPlacement.constrainAll(sceneState.widgetItems, activeWidgetBounds())
        }
        if (changed && saveIfChanged) {
            saveState()
        }
        if (changed) {
            sceneState.widgetItems.forEach { widget ->
                WidgetCaptureCoordinator.clear(widget.appWidgetId)
            }
            if (::widgetRenderer.isInitialized) {
                sceneState.widgetItems.forEach { widget ->
                    widgetRenderer.invalidateLayoutForWidget(widget.appWidgetId)
                }
            }
        }
    }

    private fun wallpaperFloorCropAspect(): Pair<Float, Float> {
        return if (isFlatFloorMode) {
            floorHalfWidth to floorHalfDepth
        } else {
            1f to 1f
        }
    }

    private fun syncWallpaperFloorCrop(reloadTexture: Boolean = true) {
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("use_wallpaper_as_floor", false)) {
            return
        }
        val (cropW, cropH) = wallpaperFloorCropAspect()
        val recropped = WallpaperFloorProvider.updateFloorCropAspect(cropW, cropH)
        if (recropped && reloadTexture) {
            reloadFloorTexture()
        }
    }

    fun saveDeskState() = saveState()

    private fun saveState() {
        if (!persistenceReady) return
        sceneState.recentsPile?.let { pile ->
            RecentsPreferences.saveFromPile(pile, context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE))
        }
        repositoryScope.launch {
            saveMutex.withLock {
                repository.saveState(sceneState)
            }
        }
    }

    fun saveCustomCameraDefault() {
        camera.saveAsDefault()
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat("cam_def_pos_x", camera.customDefaultPos[0])
            putFloat("cam_def_pos_y", camera.customDefaultPos[1])
            putFloat("cam_def_pos_z", camera.customDefaultPos[2])
            putFloat("cam_def_lat_x", camera.customDefaultLookAt[0])
            putFloat("cam_def_lat_y", camera.customDefaultLookAt[1])
            putFloat("cam_def_lat_z", camera.customDefaultLookAt[2])
            apply()
        }
    }

    fun resetCameraDefaults() {
        camera.resetToAbsoluteDefaults()
        clearSavedCustomCameraPrefs(context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE))
    }

    private fun clearSavedCustomCameraPrefs(prefs: android.content.SharedPreferences) {
        prefs.edit().apply {
            remove("cam_def_pos_x")
            remove("cam_def_pos_y")
            remove("cam_def_pos_z")
            remove("cam_def_lat_x")
            remove("cam_def_lat_y")
            remove("cam_def_lat_z")
            apply()
        }
    }

    /** Top-down camera saved from flat-floor mode is unusable in the 3D room view. */
    private fun leavingFlatFloorCamera(): Boolean =
        !isFlatFloorMode && camera.currentViewMode == CameraManager.ViewMode.FLOOR

    private fun transitionCameraOutOfFlatFloor(
        displayProfile: ScreenMetrics.DisplayProfile,
        prefs: android.content.SharedPreferences,
        reason: String,
    ) {
        if (physicsEngine.isInfiniteMode) {
            camera.transitionToProfileDefaults(displayProfile)
            (context as? LauncherActivity)?.showResetButton(false)
            CameraDiagnostics.log(
                camera,
                reason,
                "source=leavingFlatFloor infinite orientation=${displayProfile.orientationKey}",
            )
            return
        }

        if (prefs.contains("cam_def_pos_x")) {
            camera.customDefaultPos[0] = prefs.getFloat("cam_def_pos_x", camera.ABSOLUTE_DEFAULT_POS[0])
            camera.customDefaultPos[1] = prefs.getFloat("cam_def_pos_y", camera.ABSOLUTE_DEFAULT_POS[1])
            camera.customDefaultPos[2] = prefs.getFloat("cam_def_pos_z", camera.ABSOLUTE_DEFAULT_POS[2])
            camera.customDefaultLookAt[0] = prefs.getFloat("cam_def_lat_x", camera.ABSOLUTE_DEFAULT_LOOKAT[0])
            camera.customDefaultLookAt[1] = prefs.getFloat("cam_def_lat_y", camera.ABSOLUTE_DEFAULT_LOOKAT[1])
            camera.customDefaultLookAt[2] = prefs.getFloat("cam_def_lat_z", camera.ABSOLUTE_DEFAULT_LOOKAT[2])
            if (isFlatFloorStyleSavedCamera(camera.customDefaultPos, camera.customDefaultLookAt)) {
                BumpDeskLog.w(
                    BumpDeskLog.Tag.CAMERA,
                    reason,
                    "clearing flat-floor cam_def while leaving flat floor",
                )
                clearSavedCustomCameraPrefs(prefs)
                camera.transitionToProfileDefaults(displayProfile)
            } else {
                camera.transitionToCustomDefaults()
            }
            prefs.edit()
                .putString(ScreenMetrics.PREFS_LAST_ORIENTATION, displayProfile.orientationKey)
                .apply()
            (context as? LauncherActivity)?.showResetButton(false)
            CameraDiagnostics.log(camera, reason, "source=leavingFlatFloor savedCustomCamera")
            return
        }

        val anchor = OrientationCameraAnchor.load(context, displayProfile.orientationKey)
        if (anchor != null) {
            camera.transitionToAnchor(anchor)
            CameraDiagnostics.log(
                camera,
                reason,
                "source=leavingFlatFloor userAnchor orientation=${displayProfile.orientationKey}",
            )
        } else {
            camera.transitionToProfileDefaults(displayProfile)
            CameraDiagnostics.log(
                camera,
                reason,
                "source=leavingFlatFloor profile orientation=${displayProfile.orientationKey}",
            )
        }
        prefs.edit()
            .putString(ScreenMetrics.PREFS_LAST_ORIENTATION, displayProfile.orientationKey)
            .apply()
        (context as? LauncherActivity)?.showResetButton(false)
    }

    private fun isFlatFloorStyleSavedCamera(pos: FloatArray, lookAt: FloatArray): Boolean {
        val dy = pos[1] - lookAt[1]
        if (dy < 15f) return false
        val horizontal = hypot(pos[0] - lookAt[0], pos[2] - lookAt[2])
        return horizontal < 3f
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

                sceneState.recentsPile = sceneState.piles.find { it.isSystem && it.name == "Recents" }
                sceneState.appDrawerItem = sceneState.bumpItems.find { it.appearance.type == BumpItem.Type.APP_DRAWER }
            }

            persistenceReady = true

            val reconcile = DesktopReconciliation.reconcile(
                sceneState,
                boundX = floorHalfWidth,
                boundZ = floorHalfDepth,
            )
            if (reconcile.removedDuplicateListEntries > 0 ||
                reconcile.clampedFloorItems > 0 ||
                reconcile.clampedPiles > 0
            ) {
                BumpDeskLog.i(
                    BumpDeskLog.Tag.ICON_GROUP,
                    "reconcileDesktop",
                    "dupes=${reconcile.removedDuplicateListEntries} " +
                        "clampedItems=${reconcile.clampedFloorItems} clampedPiles=${reconcile.clampedPiles}",
                )
            }

            onComplete()
            sceneState.widgetItems.forEach { widget ->
                BumpDeskLog.i(
                    BumpDeskLog.Tag.WIDGET,
                    "pinSurface",
                    "restored id=${widget.appWidgetId} " +
                        WidgetSurfaceTransform.surfaceAttachmentDescription(widget, ROOM_SIZE),
                )
            }
            glSurfaceView?.requestRender()
        }
    }

    fun setAllAppsList(apps: List<AppInfo>) {
        sceneState.allAppsList.clear()
        sceneState.allAppsList.addAll(apps)
        
        loadSavedState(apps) {
            updateSettings()
            glSurfaceView?.post {
                (context as? LauncherActivity)?.restoreSavedWidgets()
            }
        }
    }

    fun resetDesktopLayout() {
        sceneState.withWriteLock {
            sceneState.bumpItems.removeAll { it.appearance.type != BumpItem.Type.APP_DRAWER }
            sceneState.piles.removeAll { !it.isSystem }
        }
        BumpDeskLog.i(BumpDeskLog.Tag.ICON_GROUP, "resetDesktopLayout", "cleared user icons and piles")
        saveState()
        glSurfaceView?.requestRender()
    }

    fun addAppToDesk(app: AppInfo) {
        if (!sceneState.isAlreadyOnDesktop(app)) {
            val x = (Math.random().toFloat() * 4f) - 2f
            val z = (Math.random().toFloat() * 4f) - 2f
            sceneState.bumpItems.add(BumpItem(appInfo = app, position = Vector3(x, 0.05f, z), scale = physicsEngine.defaultScale))
            saveState()
        }
    }

    fun addStickyNote(text: String, x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        val pos = if (hit != null) Vector3.fromArray(hit.second) else Vector3(0f, 0.05f, 0f)
        val noteScale = (physicsEngine.defaultScale * TextRenderer.STICKY_NOTE_DEFAULT_SCALE_MULTIPLIER).coerceAtLeast(1.0f)
        sceneState.bumpItems.add(
            BumpItem(
                type = BumpItem.Type.STICKY_NOTE,
                text = text,
                position = pos,
                surface = hit?.first ?: BumpItem.Surface.FLOOR,
                color = floatArrayOf(1f, 1f, 1f, 1f),
                scale = noteScale,
            ),
        )
        saveState()
    }

    fun addPhotoFrame(uri: String, x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        val pos = if (hit != null) Vector3.fromArray(hit.second) else Vector3(0f, 0.05f, 0f)
        sceneState.bumpItems.add(BumpItem(type = BumpItem.Type.PHOTO_FRAME, text = uri, position = pos, surface = hit?.first ?: BumpItem.Surface.FLOOR, scale = physicsEngine.defaultScale * 2.0f))
        saveState()
    }

    fun addWebWidget(url: String, x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        val pos = if (hit != null) Vector3.fromArray(hit.second) else Vector3(0f, 0.05f, 0f)
        sceneState.bumpItems.add(BumpItem(type = BumpItem.Type.WEB_WIDGET, text = url, position = pos, surface = hit?.first ?: BumpItem.Surface.FLOOR, scale = physicsEngine.defaultScale * 3.0f))
        saveState()
    }

    fun performSearch(query: String) { searchQuery = query.lowercase(); glSurfaceView?.requestRender() }

    fun addWidgetAt(appWidgetId: Int, hostView: android.appwidget.AppWidgetHostView, x: Float, y: Float) {
        sceneState.widgetViews[appWidgetId] = hostView
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)
        val aspect = info?.let { WidgetUtils.aspectRatioFromProvider(it) } ?: 1f
        val defaultSize = info?.let { WidgetUtils.normalizeGridSize(it, WidgetUtils.defaultWorldSize(it)) }
            ?: WidgetUtils.defaultSizeForAspect(aspect)
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val hit = interactionManager.findWallOrFloorHit(rS, rE, 0.05f)
        val rawPos = hit?.second?.clone() ?: floatArrayOf(0f, 3f, -ROOM_SIZE + 0.1f)
        if (hit != null) {
            when (hit.first) {
                BumpItem.Surface.BACK_WALL -> rawPos[2] = -ROOM_SIZE + 0.1f
                BumpItem.Surface.LEFT_WALL -> rawPos[0] = -ROOM_SIZE + 0.1f
                BumpItem.Surface.RIGHT_WALL -> rawPos[0] = ROOM_SIZE - 0.1f
                BumpItem.Surface.FLOOR -> rawPos[1] = 0.1f
            }
        }
        val surface = hit?.first ?: if (isFlatFloorMode) BumpItem.Surface.FLOOR else BumpItem.Surface.BACK_WALL
        val widget = WidgetItem(
            appWidgetId = appWidgetId,
            position = Vector3.fromArray(rawPos),
            size = defaultSize,
            surface = surface,
            aspectRatio = aspect,
        )
        sceneState.widgetItems.add(widget)
        constrainWidgetsToActiveBounds()
        BumpDeskLog.i(
            BumpDeskLog.Tag.WIDGET,
            "pinSurface",
            "placed id=$appWidgetId ${WidgetSurfaceTransform.surfaceAttachmentDescription(widget, ROOM_SIZE)} " +
                "size=${defaultSize.x}x${defaultSize.z} aspect=$aspect provider=${info?.provider?.className}",
        )
        saveState()
    }

    fun removeWidget(widget: WidgetItem) {
        val id = widget.appWidgetId
        val view = sceneState.widgetViews.remove(id)
        sceneState.widgetItems.remove(widget)
        WidgetCaptureCoordinator.clear(id)
        if (::widgetRenderer.isInitialized) {
            glSurfaceView?.queueEvent {
                widgetRenderer.releaseWidgetTexture(id, widget)
            }
        } else {
            widget.textureId = -1
        }
        saveState()
        glSurfaceView?.post {
            (context as? LauncherActivity)?.releaseWidgetHost(id, view)
        }
        glSurfaceView?.requestRender()
    }

    fun scaleWidget(widget: WidgetItem, factor: Float) {
        widget.scale = (widget.scale * factor).coerceIn(0.5f, 2.5f)
        constrainWidgetsToActiveBounds()
        saveState()
        glSurfaceView?.requestRender()
    }

    fun refreshWidgetAfterResize(widget: WidgetItem) {
        WidgetCaptureCoordinator.clear(widget.appWidgetId)
        if (::widgetRenderer.isInitialized) {
            widgetRenderer.invalidateLayoutForWidget(widget.appWidgetId)
        }
        constrainWidgetsToActiveBounds()
        saveState()
        glSurfaceView?.post {
            (context as? LauncherActivity)?.configureWidgetHostView(widget.appWidgetId, invalidateTexture = false)
        }
        glSurfaceView?.requestRender()
    }

    fun togglePin(item: BumpItem) { item.transform.isPinned = !item.transform.isPinned; saveState() }

    fun scaleLassoSelection(items: List<BumpItem>, grow: Boolean) {
        sceneState.withWriteLock {
            val targets = PileOperations.releaseItemsToDesktopUnlocked(sceneState, items)
            SelectionOperations.scaleItems(targets, grow)
        }
        sceneState.groupSelectedItems = null
        saveState()
        glSurfaceView?.requestRender()
    }

    fun pinLassoSelection(items: List<BumpItem>, pinned: Boolean) {
        sceneState.withWriteLock {
            val targets = PileOperations.releaseItemsToDesktopUnlocked(sceneState, items)
            SelectionOperations.setPinned(targets, pinned)
        }
        sceneState.groupSelectedItems = null
        saveState()
        glSurfaceView?.requestRender()
    }

    fun prepareGroupMove(items: List<BumpItem>) {
        sceneState.withWriteLock {
            val targets = PileOperations.releaseItemsToDesktopUnlocked(sceneState, items)
            sceneState.groupSelectedItems = targets
        }
        glSurfaceView?.requestRender()
    }

    fun deleteLassoSelection(items: List<BumpItem>) {
        PileOperations.removeItemsFromScene(sceneState, items)
        sceneState.groupSelectedItems = null
        saveState()
        glSurfaceView?.requestRender()
    }

    fun clearGroupSelection() {
        sceneState.groupSelectedItems = null
    }

    fun updateRecents(recents: List<AppInfo>) {
        BumpDeskLog.enter(BumpDeskLog.Tag.RECENTS, "updateRecents", "count=${recents.size}")
        sceneState.withWriteLock {
            if (sceneState.recentsPile == null) {
                sceneState.recentsPile = Pile(
                    mutableListOf(),
                    Vector3(-6f, 0.05f, 6f),
                    name = "Recents",
                    layoutMode = Pile.LayoutMode.FOLDER,
                    surface = BumpItem.Surface.FLOOR,
                    isSystem = true,
                )
                sceneState.piles.add(sceneState.recentsPile!!)
                BumpDeskLog.d(BumpDeskLog.Tag.RECENTS, "updateRecents", "created system recents pile")
            }

            val pile = sceneState.recentsPile!!
            val oldItems = pile.items.toList()
            val newItems = recents.map { appInfo ->
                val existing = findExistingRecentsItem(oldItems, appInfo)
                    ?: findExistingRecentsItem(sceneState.bumpItems, appInfo)
                    ?: findExistingRecentsItem(sceneState.piles.flatMap { it.items }, appInfo)

                val item = existing?.copy() ?: BumpItem(type = BumpItem.Type.RECENT_APP, appInfo = appInfo)

                item.apply {
                    this.appInfo = appInfo
                    appearance.type = BumpItem.Type.RECENT_APP
                    transform.position = pile.position.copy()
                    transform.surface = pile.surface
                    val prev = existing?.appData?.appInfo
                    if (prev?.snapshot != appInfo.snapshot ||
                        prev?.taskId != appInfo.taskId ||
                        prev?.packageName != appInfo.packageName
                    ) {
                        appearance.textureId = -1
                    }
                }
                item
            }

            pile.items.clear()
            pile.items.addAll(newItems)
            configureRecentsPileForCurrentMode()
            pile.reconcilePinnedOpenState()
            FolderDrawerStyle.syncRecentsDrawerItemScales(pile)

            BumpDeskLog.d(BumpDeskLog.Tag.RECENTS, "updateRecents", "tiles=${newItems.size}")
            newItems.forEach { item ->
                BumpDeskLog.d(
                    BumpDeskLog.Tag.RECENTS,
                    "updateRecents",
                    "tile pkg=${item.appData?.appInfo?.packageName} taskId=${item.appData?.appInfo?.taskId}",
                )
            }
        }

        glSurfaceView?.requestRender()
    }

    private fun findExistingRecentsItem(candidates: List<BumpItem>, appInfo: AppInfo): BumpItem? {
        if (appInfo.taskId != -1) {
            candidates.find { it.appData?.appInfo?.taskId == appInfo.taskId }?.let { return it }
        }
        return candidates.find {
            val info = it.appData?.appInfo ?: return@find false
            info.packageName == appInfo.packageName &&
                (appInfo.taskId == -1 || info.taskId == -1)
        }
    }

    private fun recentsUsesFloorLayout(): Boolean =
        isFlatFloorMode || physicsEngine.isInfiniteMode

    private fun displayAspectRatio(): Float {
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            return surfaceWidth.toFloat() / surfaceHeight
        }
        val profile = ScreenMetrics.from(context)
        return profile.widthPx.toFloat() / profile.heightPx.coerceAtLeast(1)
    }

    private fun flatFloorStyleBounds(): FlatFloorMode.Bounds =
        FlatFloorMode.computeFloorBounds(
            FlatFloorMode.DEFAULT_EYE_Y,
            FlatFloorMode.DEFAULT_EYE_Z,
            FlatFloorMode.DEFAULT_FOV,
            displayAspectRatio(),
            FlatFloorMode.DEFAULT_ZOOM,
        )

    private fun recentsConstrainHalfExtents(): Pair<Float, Float> {
        if (isFlatFloorMode) return floorHalfWidth to floorHalfDepth
        if (physicsEngine.isInfiniteMode) {
            val bounds = flatFloorStyleBounds()
            return bounds.halfX to bounds.halfZ
        }
        return ROOM_SIZE to ROOM_SIZE
    }

    private fun configureRecentsPileForCurrentMode() {
        val pile = sceneState.recentsPile ?: return
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("show_recent_apps", true)) {
            pile.items.clear()
            pile.isExpanded = false
            pile.isPinnedOpen = false
            return
        }

        RecentsPreferences.applyToPile(pile, prefs, recentsUsesFloorLayout(), ROOM_SIZE)

        val useFloorRecents = recentsUsesFloorLayout()
        val targetSurface = if (useFloorRecents) BumpItem.Surface.FLOOR else pile.surface
        val needsTextureRebuild =
            pile.layoutMode != Pile.LayoutMode.FOLDER ||
                pile.surface != targetSurface ||
                (useFloorRecents && pile.position.y > 1f) ||
                (!useFloorRecents && pile.surface == BumpItem.Surface.FLOOR && pile.position.y <= 1f)
        if (needsTextureRebuild) {
            pile.items.forEach { item ->
                item.appearance.textureId = -1
            }
            pile.previewTextureId = -1
            pile.previewSignature = ""
        }

        pile.layoutMode = Pile.LayoutMode.FOLDER
        if (useFloorRecents) {
            val wasOnWall = pile.recentsOnWall()
            pile.surface = BumpItem.Surface.FLOOR
            if (wasOnWall ||
                (!prefs.contains(RecentsPreferences.PREF_POS_X) &&
                    (pile.position.y > 1f || pile.position.z < -ROOM_SIZE / 2f))
            ) {
                pile.position = Vector3(-6f, 0.05f, 6f)
            } else {
                pile.position = pile.position.copy(y = 0.05f)
            }
        } else {
            if (pile.surface == BumpItem.Surface.FLOOR ||
                (!prefs.contains(RecentsPreferences.PREF_SURFACE) && pile.position.y <= 1f)
            ) {
                pile.surface = BumpItem.Surface.BACK_WALL
                if (!prefs.contains(RecentsPreferences.PREF_POS_X)) {
                    pile.position = Vector3(0f, 4f, -ROOM_SIZE + 0.6f)
                } else {
                    pile.position = pile.position.copy(z = -ROOM_SIZE + 0.6f)
                }
            } else if (pile.surface == BumpItem.Surface.BACK_WALL) {
                pile.position = pile.position.copy(z = -ROOM_SIZE + 0.6f)
            } else if (pile.surface == BumpItem.Surface.LEFT_WALL) {
                pile.position = pile.position.copy(x = -ROOM_SIZE + 0.6f)
            } else if (pile.surface == BumpItem.Surface.RIGHT_WALL) {
                pile.position = pile.position.copy(x = ROOM_SIZE - 0.6f)
            }
        }
        if (!pile.isPinnedOpen) {
            pile.isExpanded = false
        }
        pile.scrollIndex = pile.scrollIndex.coerceIn(
            0,
            (FolderDrawerStyle.totalPages(pile) - 1).coerceAtLeast(0),
        )
        pile.items.forEach { item ->
            item.transform.surface = pile.surface
            if (!pile.layoutAsExpandedDrawer()) {
                item.transform.scale = physicsEngine.defaultScale
                item.transform.position = pile.position.copy()
            }
        }
        BumpDeskLog.d(
            BumpDeskLog.Tag.RECENTS,
            "configureRecentsPile",
            "mode=${when {
                isFlatFloorMode -> "flatFloor"
                physicsEngine.isInfiniteMode -> "infinite"
                else -> "room"
            }} view=${pile.recentsViewMode} pinned=${pile.isPinnedOpen} items=${pile.items.size}",
        )
        pile.reconcilePinnedOpenState()
        if (pile.isRecentsPile() && pile.isPinnedOpen) {
            val (halfX, halfZ) = recentsConstrainHalfExtents()
            FolderDrawerStyle.constrainRecentsPilePosition(
                pile,
                halfX,
                halfZ,
                halfX,
            )
        }
        FolderDrawerStyle.syncRecentsDrawerItemScales(pile)
    }

    fun focusRecentsPile(onShowResetButton: (Boolean) -> Unit) {
        val pile = sceneState.recentsPile ?: return
        pile.reconcilePinnedOpenState()
        if (!pile.layoutAsExpandedDrawer()) {
            collapseNonPinnedPiles()
            pile.scrollIndex = 0
            pile.nameTextureId = -1
            pile.isExpanded = true
        }
        if (pile.recentsOnWall()) {
            // Stay in the room view — wall Recents behaves like a pinned widget panel.
            onShowResetButton(false)
        } else {
            camera.focusOnFolder(
                pile.position.toFloatArray(),
                pile.scale,
                folderPanelHalfExtent(pile),
            )
            onShowResetButton(true)
        }
        glSurfaceView?.requestRender()
    }

    /** Collapse expanded piles but leave a pinned-open Recents drawer in place. */
    private fun collapseNonPinnedPiles() {
        AllAppsDrawer.clearForCollapse(sceneState)
        sceneState.piles.forEach { pile ->
            if (pile.isRecentsPile() && pile.isPinnedOpen) {
                pile.reconcilePinnedOpenState()
            } else {
                pile.isExpanded = false
            }
        }
    }

    fun setRecentsViewMode(mode: Pile.RecentsViewMode) {
        val pile = sceneState.recentsPile ?: return
        pile.recentsViewMode = mode
        pile.items.forEach { it.appearance.textureId = -1 }
        RecentsPreferences.saveFromPile(pile, context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE))
        glSurfaceView?.requestRender()
    }

    fun toggleRecentsPinnedOpen() {
        val pile = sceneState.recentsPile ?: return
        pile.isPinnedOpen = !pile.isPinnedOpen
        if (pile.isPinnedOpen) {
            pile.isExpanded = true
            pile.previewTextureId = -1
            pile.previewSignature = ""
        } else {
            pile.isExpanded = false
            if (camera.currentViewMode == CameraManager.ViewMode.FOLDER_EXPANDED) {
                dismissExpandedPile()
            }
        }
        RecentsPreferences.saveFromPile(pile, context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE))
        glSurfaceView?.requestRender()
    }

    fun collapseRecentsDrawer() {
        val pile = sceneState.recentsPile ?: return
        pile.isPinnedOpen = false
        pile.isExpanded = false
        RecentsPreferences.saveFromPile(pile, context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE))
        if (camera.currentViewMode == CameraManager.ViewMode.FOLDER_EXPANDED) {
            dismissExpandedPile()
        }
        glSurfaceView?.requestRender()
    }

    private fun handleRecentsTaskCardTap(item: BumpItem, rS: FloatArray, rE: FloatArray): Boolean {
        val scale = item.transform.scale
        val heightMult = 1.6f
        val cardZ = item.transform.position.z - 0.08f
        val t = (cardZ - rS[2]) / (rE[2] - rS[2])
        if (t <= 0) return false
        val hitX = rS[0] + t * (rE[0] - rS[0])
        val hitY = rS[1] + t * (rE[1] - rS[1])
        val halfW = scale
        val halfH = scale * heightMult
        val cx = item.transform.position.x
        val cy = item.transform.position.y
        if (abs(hitX - cx) > halfW || abs(hitY - cy) > halfH) return false

        val u = (hitX - (cx - halfW)) / (2f * halfW)
        val v = 1f - (hitY - (cy - halfH)) / (2f * halfH)

        if (u > 0.85f && v < 0.15f) {
            (context as? LauncherActivity)?.removeTask(item.appData?.appInfo?.taskId ?: -1)
            sceneState.recentsPile?.items?.remove(item)
            glSurfaceView?.requestRender()
            return true
        }
        if (v > 0.76f && v < 0.89f) {
            val pkg = item.appData?.appInfo?.packageName ?: ""
            if (u < 0.25f) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } else if (u < 0.5f) {
                (context as? LauncherActivity)?.launchApp(item, LauncherActivity.WINDOWING_MODE_FULLSCREEN)
            } else if (u < 0.75f) {
                (context as? LauncherActivity)?.launchApp(item, LauncherActivity.WINDOWING_MODE_FREEFORM)
            } else {
                (context as? LauncherActivity)?.launchApp(item, LauncherActivity.WINDOWING_MODE_PINNED)
            }
            return true
        }
        return false
    }

    fun categorizeAllApps() {
        BumpDeskLog.enter(BumpDeskLog.Tag.ICON_GROUP, "categorizeAllApps")
        val apps = sceneState.bumpItems.filter { it.appearance.type == BumpItem.Type.APP && it.appData?.appInfo != null }
        if (apps.isEmpty()) {
            BumpDeskLog.w(BumpDeskLog.Tag.ICON_GROUP, "categorizeAllApps", "skipped | no apps")
            return
        }
        
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
        BumpDeskLog.exit(BumpDeskLog.Tag.ICON_GROUP, "categorizeAllApps", "piles=${sceneState.piles.size}")
    }

    @Volatile
    private var pendingThemeReload = false
    @Volatile
    private var pendingFloorReload = false

    fun reloadFloorTexture() {
        pendingFloorReload = true
        scheduleGlReload()
    }

    fun reloadTheme() {
        pendingThemeReload = true
        scheduleGlReload()
    }

    private fun scheduleGlReload() {
        saveState()
        glSurfaceView?.queueEvent { applyPendingGlReloads() }
        glSurfaceView?.requestRender()
    }

    fun applyPendingGlReloads() {
        if (!isGlContextCurrent()) {
            BumpDeskLog.d(BumpDeskLog.Tag.THEME, "applyPendingGlReloads", "deferred | no GL context")
            return
        }
        if (pendingThemeReload) {
            if (performThemeReload()) {
                pendingThemeReload = false
            }
            return
        }
        if (pendingFloorReload) {
            performFloorReload()
            pendingFloorReload = false
        }
    }

    private fun isGlContextCurrent(): Boolean =
        EGL14.eglGetCurrentContext() != EGL14.EGL_NO_CONTEXT

    private fun performFloorReload() {
        BumpDeskLog.enter(BumpDeskLog.Tag.WALLPAPER, "reloadFloorTexture")
        textureManager.evictCachedTexture("wallpaper:floor")
        floorTextureId = ThemeManager.getFloorTexture(context, textureManager)
        BumpDeskLog.exit(BumpDeskLog.Tag.WALLPAPER, "reloadFloorTexture", "floorTextureId=$floorTextureId")
        glSurfaceView?.requestRender()
    }

    private fun performThemeReload(): Boolean {
        BumpDeskLog.enter(BumpDeskLog.Tag.THEME, "reloadTheme", "theme=${ThemeManager.currentThemeName}")
        try {
            val envCode = ThemeManager.getShaderCode(context, "environment") ?: ""
            val newShader = DefaultShader(envCode)
            if (!newShader.isValid()) {
                BumpDeskLog.fail(BumpDeskLog.Tag.THEME, "reloadTheme", "shader compile/link failed, keeping previous resources")
                return false
            }

            textureManager.clearCache()
            floorTextureId = -1
            wallTextureIds = IntArray(4) { -1 }

            sceneState.withReadLock {
                sceneState.bumpItems.forEach { it.appearance.textureId = -1 }
                sceneState.piles.forEach { p ->
                    p.items.forEach { it.appearance.textureId = -1 }
                    p.nameTextureId = -1
                    PileFolderIcons.invalidatePreview(p)
                }
                sceneState.widgetItems.forEach { it.textureId = -1 }
            }

            shader = newShader
            roomRenderer = RoomRenderer(shader!!)
            overlayRenderer = OverlayRenderer(shader!!, textureManager)

            itemRenderer = ItemRenderer(context, shader!!, textureManager, sceneState)
            widgetRenderer = WidgetRenderer(context, shader!!, textureManager)
            pileRenderer = PileRenderer(context, shader!!, textureManager, itemRenderer, overlayRenderer, sceneState)
            uiRenderer = UIRenderer(shader!!, overlayRenderer)

            loadThemeTextures()
            BumpDeskLog.exit(
                BumpDeskLog.Tag.THEME,
                "reloadTheme",
                "floorTextureId=$floorTextureId walls=${wallTextureIds.joinToString()}"
            )
            glSurfaceView?.requestRender()
            return true
        } catch (e: Exception) {
            BumpDeskLog.fail(BumpDeskLog.Tag.THEME, "reloadTheme", "GL reload failed", e)
            return false
        }
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.02f, 0.02f, 0.02f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        
        performThemeReload()
        lassoRenderer = LassoRenderer(LassoShader())
    }

    private fun loadThemeTextures() {
        floorTextureId = ThemeManager.getFloorTexture(context, textureManager)
        wallTextureIds = ThemeManager.getWallTextures(context, textureManager)
        
        uiAssets = UIRenderer.UIAssets(
            closeBtn = textureManager.loadTextureFromBitmap(
                TextRenderer.createIconButtonBitmap("✕")
            ),
            arrowLeft = textureManager.loadTextureFromBitmap(
                TextRenderer.createIconButtonBitmap("‹")
            ),
            arrowRight = textureManager.loadTextureFromBitmap(
                TextRenderer.createIconButtonBitmap("›")
            ),
            scrollUp = ThemeManager.loadOptionalWidgetTexture(context, textureManager, "scrollUp"),
            scrollDown = ThemeManager.loadOptionalWidgetTexture(context, textureManager, "scrollDown")
        )
    }

    override fun onDrawFrame(unused: GL10) {
        if (pendingThemeReload || pendingFloorReload) {
            applyPendingGlReloads()
        }
        if (shader == null || shader?.isValid() != true) {
            glSurfaceView?.requestRender()
            return
        }

        if (AllAppsDrawer.reconcile(sceneState, camera.currentViewMode)) {
            (context as? LauncherActivity)?.showResetButton(false)
        }
        if (ExpandedFolderDrawer.reconcile(sceneState, camera.currentViewMode)) {
            (context as? LauncherActivity)?.showResetButton(false)
        }

        frameCount++
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        camera.update(); camera.setViewMatrix(viewMatrix)
        
        Matrix.perspectiveM(projectionMatrix, 0, camera.fieldOfView, surfaceWidth.toFloat() / surfaceHeight, 0.1f, 100f)
        
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.invertM(interactionManager.invertedVPMatrix, 0, vPMatrix, 0)
        
        val isAnimatedTheme = ThemeManager.currentThemeName == "BumpDesk Animated"
        roomRenderer.draw(
            vPMatrix,
            floorTextureId,
            wallTextureIds,
            lightPos,
            interactionManager.isInfiniteMode,
            ROOM_SIZE,
            ROOM_HEIGHT,
            floorHalfWidth,
            floorHalfDepth,
            frameCount.toFloat(),
            isAnimatedTheme
        )
        
        val onUpdateTexture: (Runnable) -> Unit = { event -> glSurfaceView?.queueEvent(event) }
        val runOnUiThread: (Runnable) -> Unit = { runnable -> glSurfaceView?.post(runnable) }

        if (shader != null) {
            itemRenderer.drawItems(
                vPMatrix,
                AllAppsDrawer.visibleBumpItems(sceneState),
                lightPos,
                searchQuery,
                onUpdateTexture,
            )
            widgetRenderer.drawWidgets(vPMatrix, sceneState.widgetItems, sceneState.widgetViews, frameCount, sceneState.selectedWidget, runOnUiThread, onUpdateTexture)
            pileRenderer.drawPiles(
                vPMatrix,
                sceneState.piles,
                lightPos,
                searchQuery,
                camera.currentViewMode,
                onUpdateTexture,
                floorHalfWidth,
                floorHalfDepth,
                ROOM_SIZE,
            )
            uiRenderer.drawOverlays(
                vPMatrix,
                sceneState,
                camera,
                uiAssets,
                lightPos,
                searchQuery,
                textureManager,
                floorHalfWidth,
                floorHalfDepth,
                ROOM_SIZE,
            )
        }
        
        if (interactionManager.lassoPoints.isNotEmpty()) lassoRenderer.draw(vPMatrix, interactionManager.lassoPoints)
        
        if (isAnimatedTheme) {
            glSurfaceView?.requestRender()
        } else if (camera.isAnimating()) {
            glSurfaceView?.requestRender()
        }
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
        if (eventConsumed || interactionManager.isResizingRecentsDrawer()) {
            glSurfaceView?.requestRender()
        }
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
        if (AllAppsDrawer.removeIfEmpty(sceneState)) {
            dismissExpandedPile()
        }
        saveState()
    }

    fun gridSelectedItems(items: List<BumpItem>, mode: GridLayout) {
        BumpDeskLog.enter(BumpDeskLog.Tag.ICON_GROUP, "gridSelectedItems", "requested=${items.size} mode=$mode")
        sceneState.withWriteLock {
            val targets = PileOperations.releaseItemsToDesktopUnlocked(sceneState, items)
            if (targets.size < 2) {
                BumpDeskLog.w(
                    BumpDeskLog.Tag.ICON_GROUP,
                    "gridSelectedItems",
                    "skipped | need at least 2 items, got ${targets.size}",
                )
                return@withWriteLock
            }

            val maxScale = targets.maxOf { it.transform.scale }
            val spacing = when (mode) {
                GridLayout.ROW -> maxOf(physicsEngine.gridSpacingBase, maxScale * 2.55f)
                GridLayout.COLUMN -> maxOf(physicsEngine.gridSpacingBase, maxScale * 3.25f)
                GridLayout.GRID -> maxOf(physicsEngine.gridSpacingBase, maxScale * 2.55f)
            }
            val startX = targets.map { it.transform.position.x }.average().toFloat()
            val startZ = targets.map { it.transform.position.z }.average().toFloat()
            when (mode) {
                GridLayout.GRID -> {
                    val side = Math.ceil(Math.sqrt(targets.size.toDouble())).toInt()
                    val offset = (side * spacing) / 2f
                    targets.forEachIndexed { i, item ->
                        item.transform.position = Vector3(
                            (startX - offset) + (i % side) * spacing,
                            0.05f,
                            (startZ - offset) + (i / side) * spacing,
                        )
                        item.transform.surface = BumpItem.Surface.FLOOR
                        item.transform.velocity = Vector3()
                    }
                }
                GridLayout.ROW -> {
                    val offset = (targets.size * spacing) / 2f
                    targets.forEachIndexed { i, item ->
                        item.transform.position = Vector3(
                            (startX - offset) + i * spacing,
                            0.05f,
                            startZ,
                        )
                        item.transform.surface = BumpItem.Surface.FLOOR
                        item.transform.velocity = Vector3()
                    }
                }
                GridLayout.COLUMN -> {
                    val offset = (targets.size * spacing) / 2f
                    targets.forEachIndexed { i, item ->
                        item.transform.position = Vector3(
                            startX,
                            0.05f,
                            (startZ - offset) + i * spacing,
                        )
                        item.transform.surface = BumpItem.Surface.FLOOR
                        item.transform.velocity = Vector3()
                    }
                }
            }
            BumpDeskLog.exit(
                BumpDeskLog.Tag.ICON_GROUP,
                "gridSelectedItems",
                "mode=$mode items=${targets.size} spacing=${"%.2f".format(spacing)} center=(${"%.1f".format(startX)},${"%.1f".format(startZ)})",
            )
        }
        saveState()
        sceneState.groupSelectedItems = null
        glSurfaceView?.requestRender()
    }

    fun handleSingleTap(x: Float, y: Float) {
        sceneState.recentsPile?.reconcilePinnedOpenState()
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val overlayPile = FolderDrawerStyle.resolveOverlayPile(sceneState.piles, camera.currentViewMode)
        if (overlayPile != null) {
            val isWall = overlayPile.surface != BumpItem.Surface.FLOOR
            val t = when (overlayPile.surface) {
                BumpItem.Surface.FLOOR -> {
                    val layout = FolderDrawerStyle.layout(overlayPile, floorHalfWidth, floorHalfDepth)
                    val planeY = FolderDrawerStyle.floorChromeY(overlayPile, layout)
                    (planeY - rS[1]) / (rE[1] - rS[1])
                }
                BumpItem.Surface.BACK_WALL -> (-ROOM_SIZE + 0.6f - rS[2]) / (rE[2] - rS[2])
                BumpItem.Surface.LEFT_WALL -> (-ROOM_SIZE + 0.6f - rS[0]) / (rE[0] - rS[0])
                BumpItem.Surface.RIGHT_WALL -> (ROOM_SIZE - 0.6f - rS[0]) / (rE[0] - rS[0])
                else -> -1f
            }

            if (t > 0) {
                val hitX = rS[0] + t * (rE[0] - rS[0])
                val hitY = rS[1] + t * (rE[1] - rS[1])
                val hitZ = rS[2] + t * (rE[2] - rS[2])

                if (overlayPile.surface == BumpItem.Surface.FLOOR) {
                    val chromeHit = FolderDrawerStyle.intersectFloorDrawerChromePlane(
                        overlayPile,
                        rS,
                        rE,
                        floorHalfWidth,
                        floorHalfDepth,
                    )
                    val testX = chromeHit?.get(0) ?: hitX
                    val testZ = chromeHit?.get(1) ?: hitZ
                    val drawerHit = FolderDrawerStyle.hitTestFloorDrawer(
                        overlayPile,
                        testX,
                        testZ,
                        floorHalfWidth,
                        floorHalfDepth,
                    )
                    when (drawerHit.kind) {
                        FolderDrawerStyle.Hit.CLOSE -> {
                            if (overlayPile.isRecentsPile() && overlayPile.isPinnedOpen) {
                                collapseRecentsDrawer()
                            } else {
                                dismissExpandedPile()
                            }
                            return
                        }
                        FolderDrawerStyle.Hit.TITLE -> {
                            if (overlayPile.isRecentsPile()) {
                                hapticManager.selection()
                                (context as? LauncherActivity)?.showRecentsMenu(x, y, overlayPile)
                            } else if (!overlayPile.isSystem) {
                                hapticManager.selection()
                                (context as? LauncherActivity)?.promptRenamePile(overlayPile) { newName ->
                                    renamePile(overlayPile, newName)
                                }
                            }
                            return
                        }
                        FolderDrawerStyle.Hit.PREV_PAGE -> {
                            overlayPile.scrollIndex--
                            FolderDrawerStyle.syncRecentsDrawerItemScales(overlayPile)
                            playSound(leafSoundId, 0.2f)
                            hapticManager.tick()
                            return
                        }
                        FolderDrawerStyle.Hit.NEXT_PAGE -> {
                            overlayPile.scrollIndex++
                            FolderDrawerStyle.syncRecentsDrawerItemScales(overlayPile)
                            playSound(leafSoundId, 0.2f)
                            hapticManager.tick()
                            return
                        }
                        FolderDrawerStyle.Hit.PAGE_DOT -> {
                            if (drawerHit.pageIndex >= 0) {
                                overlayPile.scrollIndex = drawerHit.pageIndex
                                FolderDrawerStyle.syncRecentsDrawerItemScales(overlayPile)
                                playSound(leafSoundId, 0.2f)
                                hapticManager.tick()
                            }
                            return
                        }
                        FolderDrawerStyle.Hit.NONE -> Unit
                    }
                } else if (overlayPile.showsRecentsMaterialDrawer()) {
                    val (hitPrimary, hitVertical) = when (overlayPile.surface) {
                        BumpItem.Surface.LEFT_WALL, BumpItem.Surface.RIGHT_WALL -> hitZ to hitY
                        else -> hitX to hitY
                    }
                    val drawerHit = FolderDrawerStyle.hitTestWallDrawer(
                        overlayPile,
                        hitPrimary,
                        hitVertical,
                        floorHalfWidth,
                        floorHalfDepth,
                        ROOM_SIZE,
                    )
                    when (drawerHit.kind) {
                        FolderDrawerStyle.Hit.CLOSE -> {
                            if (overlayPile.isRecentsPile() && overlayPile.isPinnedOpen) {
                                collapseRecentsDrawer()
                            } else {
                                dismissExpandedPile()
                            }
                            return
                        }
                        FolderDrawerStyle.Hit.TITLE -> {
                            hapticManager.selection()
                            (context as? LauncherActivity)?.showRecentsMenu(x, y, overlayPile)
                            return
                        }
                        FolderDrawerStyle.Hit.PREV_PAGE -> {
                            overlayPile.scrollIndex--
                            FolderDrawerStyle.syncRecentsDrawerItemScales(overlayPile)
                            playSound(leafSoundId, 0.2f)
                            hapticManager.tick()
                            return
                        }
                        FolderDrawerStyle.Hit.NEXT_PAGE -> {
                            overlayPile.scrollIndex++
                            FolderDrawerStyle.syncRecentsDrawerItemScales(overlayPile)
                            playSound(leafSoundId, 0.2f)
                            hapticManager.tick()
                            return
                        }
                        FolderDrawerStyle.Hit.PAGE_DOT -> {
                            if (drawerHit.pageIndex >= 0) {
                                overlayPile.scrollIndex = drawerHit.pageIndex
                                FolderDrawerStyle.syncRecentsDrawerItemScales(overlayPile)
                                playSound(leafSoundId, 0.2f)
                                hapticManager.tick()
                            }
                            return
                        }
                        FolderDrawerStyle.Hit.NONE -> Unit
                    }
                } else if (isWall) {
                    val width = 6f * overlayPile.scale
                    val height = 4f * overlayPile.scale
                    if (abs(hitX - (overlayPile.position.x - width + 0.5f)) < 0.5f && abs(hitY - overlayPile.position.y) < 0.5f) {
                        overlayPile.currentIndex = (overlayPile.currentIndex - 1).coerceAtLeast(0)
                        playSound(leafSoundId, 0.2f); hapticManager.tick(); return
                    }
                    if (abs(hitX - (overlayPile.position.x + width - 0.5f)) < 0.5f && abs(hitY - overlayPile.position.y) < 0.5f) {
                        overlayPile.currentIndex = (overlayPile.currentIndex + 1).coerceAtMost(overlayPile.items.size - 1)
                        playSound(leafSoundId, 0.2f); hapticManager.tick(); return
                    }
                    if (abs(hitX - overlayPile.position.x) > width || abs(hitY - overlayPile.position.y) > height) { dismissExpandedPile(); return }
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
        val item = interactionManager.findIntersectingItem(
            rS,
            rE,
            AllAppsDrawer.visibleBumpItems(sceneState),
            sceneState.piles,
        )
        if (item != null) {
            val pile = sceneState.getPileOf(item)
            if (pile != null && pile == sceneState.recentsPile && pile.showsRecentsTaskCards()) {
                if (handleRecentsTaskCardTap(item, rS, rE)) return
            }
            if (pile != null && pile == sceneState.recentsPile && pile.showsRecentsIconGrid()) {
                (context as? LauncherActivity)?.launchApp(item)
                return
            }
            if (item.appearance.type == BumpItem.Type.APP && item.appData?.appInfo?.packageName == context.packageName) { context.startActivity(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
            if (pile != null && !pile.isExpanded) {
                playSound(expandSoundId, 0.3f)
                hapticManager.selection()
                if (pile.isSystem && pile == sceneState.recentsPile) {
                    collapseNonPinnedPiles()
                    if (pile.isPinnedOpen) {
                        pile.reconcilePinnedOpenState()
                        return
                    }
                    pile.scrollIndex = 0
                    pile.nameTextureId = -1
                    pile.isExpanded = true
                    if (pile.recentsOnWall()) {
                        focusRecentsPile { (context as? LauncherActivity)?.showResetButton(it) }
                    } else {
                        camera.focusOnFolder(
                            pile.position.toFloatArray(),
                            pile.scale,
                            folderPanelHalfExtent(pile),
                        )
                        (context as? LauncherActivity)?.showResetButton(true)
                    }
                    return
                } else {
                    collapseNonPinnedPiles()
                    pile.scrollIndex = 0
                    if (pile.layoutMode == Pile.LayoutMode.FOLDER && pile.surface == BumpItem.Surface.FLOOR) {
                        pile.nameTextureId = -1
                    }
                    pile.isExpanded = true
                    camera.focusOnFolder(
                        pile.position.toFloatArray(),
                        pile.scale,
                        folderPanelHalfExtent(pile),
                    )
                }
                (context as? LauncherActivity)?.showResetButton(true); return
            }
            if (item.appearance.type == BumpItem.Type.APP_DRAWER) {
                val apps = sceneState.appsNotInAllAppsDrawer()
                if (apps.isNotEmpty()) {
                    playSound(expandSoundId, 0.3f)
                    hapticManager.selection()
                    val p = item.transform.position.copy()
                    val folderGroupScale = item.transform.scale
                    val dp = Pile(
                        apps.map { BumpItem(appInfo = it, position = p.copy(), scale = physicsEngine.defaultScale) }.toMutableList(),
                        p,
                        name = AllAppsDrawer.PILE_NAME,
                        layoutMode = Pile.LayoutMode.FOLDER,
                        isSystem = true,
                        scale = folderGroupScale,
                    )
                    dp.nameTextureId = -1
                    dp.scrollIndex = 0
                    collapseNonPinnedPiles(); dp.isExpanded = true; sceneState.piles.add(dp); camera.focusOnFolder(p.toFloatArray(), dp.scale, folderPanelHalfExtent(dp)); (context as? LauncherActivity)?.showResetButton(true)
                }
                return
            }
            if (item.appearance.type == BumpItem.Type.APP || item.appearance.type == BumpItem.Type.RECENT_APP) (context as? LauncherActivity)?.launchApp(item) else if (item.appearance.type == BumpItem.Type.STICKY_NOTE) (context as? LauncherActivity)?.promptEditStickyNote(item) else if (item.appearance.type == BumpItem.Type.PHOTO_FRAME) (context as? LauncherActivity)?.promptChangePhoto(item) else if (item.appearance.type == BumpItem.Type.WEB_WIDGET) (context as? LauncherActivity)?.promptEditWebWidget(item)
        } else {
            val collapsedRecents = sceneState.recentsPile?.takeIf { it.showsCollapsedPreview() }
            if (collapsedRecents != null &&
                interactionManager.hitTestCollapsedPilePreview(collapsedRecents, rS, rE)
            ) {
                playSound(expandSoundId, 0.3f)
                hapticManager.selection()
                focusRecentsPile { (context as? LauncherActivity)?.showResetButton(it) }
                return
            }
            if (shouldDismissFocusedViewOnEmptyTap()) {
                dismissExpandedPile()
            }
        }
        glSurfaceView?.requestRender()
    }

    private fun shouldDismissFocusedViewOnEmptyTap(): Boolean {
        if (AllAppsDrawer.isOpen(sceneState)) return true
        if (ExpandedFolderDrawer.isOpen(sceneState)) return true
        return when (camera.currentViewMode) {
            CameraManager.ViewMode.FOLDER_EXPANDED,
            CameraManager.ViewMode.WIDGET_FOCUS,
            CameraManager.ViewMode.BACK_WALL,
            CameraManager.ViewMode.LEFT_WALL,
            CameraManager.ViewMode.RIGHT_WALL -> true
            CameraManager.ViewMode.FLOOR,
            CameraManager.ViewMode.DEFAULT -> false
        }
    }

    private fun restoreFlatFloorCamera() {
        val profile = ScreenMetrics.from(context)
        val aspect = if (surfaceWidth > 0 && surfaceHeight > 0) {
            surfaceWidth.toFloat() / surfaceHeight
        } else {
            profile.widthPx.toFloat() / profile.heightPx.coerceAtLeast(1)
        }
        val bounds = FlatFloorMode.computeFloorBounds(
            FlatFloorMode.DEFAULT_EYE_Y,
            FlatFloorMode.DEFAULT_EYE_Z,
            FlatFloorMode.DEFAULT_FOV,
            aspect,
            FlatFloorMode.DEFAULT_ZOOM,
        )
        camera.transitionToFlatFloorDefaults(bounds, aspect)
        camera.syncSavedViewFromCurrent()
    }

    fun handleDoubleTap(x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        if (!isFlatFloorMode && !physicsEngine.isInfiniteMode) {
            val walls = listOf(
                Triple(BumpItem.Surface.BACK_WALL, floatArrayOf(0f, 4f, 2f), floatArrayOf(0f, 4f, -ROOM_SIZE)),
                Triple(BumpItem.Surface.LEFT_WALL, floatArrayOf(2f, 4f, 0f), floatArrayOf(-ROOM_SIZE, 4f, 0f)),
                Triple(BumpItem.Surface.RIGHT_WALL, floatArrayOf(-2f, 4f, 0f), floatArrayOf(ROOM_SIZE, 4f, 0f))
            )
            var best: Triple<BumpItem.Surface, FloatArray, FloatArray>? = null; var minT = Float.MAX_VALUE
            walls.forEach { (s, cp, la) ->
                val t = when (s) {
                    BumpItem.Surface.BACK_WALL -> (-ROOM_SIZE + 0.05f - rS[2]) / (rE[2] - rS[2])
                    BumpItem.Surface.LEFT_WALL -> (-ROOM_SIZE + 0.05f - rS[0]) / (rE[0] - rS[0])
                    BumpItem.Surface.RIGHT_WALL -> (ROOM_SIZE - 0.05f - rS[0]) / (rE[0] - rS[0])
                    else -> -1f
                }
                if (t > 0 && t < minT) {
                    val hitX = rS[0] + t * (rE[0] - rS[0])
                    val hitY = rS[1] + t * (rE[1] - rS[1])
                    val hitZ = rS[2] + t * (rE[2] - rS[2])
                    val margin = ROOM_SIZE + 0.1f
                    if (abs(hitX) <= margin && abs(hitZ) <= margin && hitY >= 0f && hitY <= ROOM_HEIGHT) {
                        minT = t; best = Triple(s, cp, la)
                    }
                }
            }
            if (best != null) {
                camera.focusOnWall(when(best!!.first) {
                    BumpItem.Surface.BACK_WALL -> CameraManager.ViewMode.BACK_WALL
                    BumpItem.Surface.LEFT_WALL -> CameraManager.ViewMode.LEFT_WALL
                    else -> CameraManager.ViewMode.RIGHT_WALL
                }, best!!.second, best!!.third)
                (context as? LauncherActivity)?.showResetButton(true); return
            }
        }
        val tf = -rS[1] / (rE[1] - rS[1])
        val (floorBoundX, floorBoundZ) = when {
            isFlatFloorMode -> floorHalfWidth to floorHalfDepth
            physicsEngine.isInfiniteMode -> {
                val bounds = flatFloorStyleBounds()
                bounds.halfX to bounds.halfZ
            }
            else -> floorHalfWidth to floorHalfDepth
        }
        if (tf > 0 &&
            abs(rS[0] + tf * (rE[0] - rS[0])) <= floorBoundX &&
            abs(rS[2] + tf * (rE[2] - rS[2])) <= floorBoundZ
        ) {
            camera.focusOnFloor()
            (context as? LauncherActivity)?.showResetButton(!isFlatFloorMode && !physicsEngine.isInfiniteMode)
            playSound(focusSoundId, 0.4f); hapticManager.selection() ; return
        }
        handleSingleTap(x, y)
    }

    fun handleLongPress(x: Float, y: Float) {
        val rS = FloatArray(4); val rE = FloatArray(4); interactionManager.calculateRay(x, y, rS, rE)
        val wHit = interactionManager.findIntersectingWidget(rS, rE, sceneState.widgetItems)
        val iHit = interactionManager.findIntersectingItem(
            rS,
            rE,
            AllAppsDrawer.visibleBumpItems(sceneState),
            sceneState.piles,
        )
        if (wHit != null && (iHit == null || wHit.second < 0.8f)) { sceneState.selectedWidget = wHit.first; (context as? LauncherActivity)?.showWidgetMenu(x, y, wHit.first); return }
        if (iHit != null) {
            val pile = sceneState.getPileOf(iHit)
            when {
                pile != null && pile.isRecentsPile() &&
                    iHit.appearance.type == BumpItem.Type.RECENT_APP &&
                    pile.layoutAsExpandedDrawer() ->
                    (context as? LauncherActivity)?.showRecentsTaskMenu(x, y, iHit)
                pile != null && pile.isRecentsPile() ->
                    (context as? LauncherActivity)?.showRecentsMenu(x, y, pile)
                pile != null && !pile.isSystem ->
                    (context as? LauncherActivity)?.showPileMenu(x, y, pile) { breakPile(pile) }
                else ->
                    (context as? LauncherActivity)?.showItemMenu(x, y, iHit)
            }
        } else {
            val pinnedRecents = interactionManager.hitTestPinnedRecentsDrawer(rS, rE, sceneState)
            if (pinnedRecents != null) {
                (context as? LauncherActivity)?.showRecentsMenu(x, y, pinnedRecents)
                return
            }
            val collapsedRecents = sceneState.recentsPile?.takeIf { it.showsCollapsedPreview() }
            if (collapsedRecents != null &&
                interactionManager.hitTestCollapsedPilePreview(collapsedRecents, rS, rE)
            ) {
                (context as? LauncherActivity)?.showRecentsMenu(x, y, collapsedRecents)
            } else {
                (context as? LauncherActivity)?.showDesktopMenu(x, y)
            }
        }
    }

    fun handleOrbit(dx: Float, dy: Float) {
        val w = surfaceWidth.coerceAtLeast(1)
        val h = surfaceHeight.coerceAtLeast(1)
        camera.handleOrbit(dx, dy, w, h)
        (context as? LauncherActivity)?.showResetButton(true)
    }

    fun createPileFromCaptured(capturedItems: List<BumpItem>, layoutMode: Pile.LayoutMode = Pile.LayoutMode.STACK) {
        PileOperations.createPileFromCaptured(sceneState, capturedItems, layoutMode)
        sceneState.groupSelectedItems = null
        saveState()
    }

    fun renamePile(pile: Pile, newName: String) {
        if (pile.isSystem) return
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        pile.name = trimmed
        pile.nameTextureId = -1
        if (pile.layoutMode == Pile.LayoutMode.FOLDER) {
            PileFolderIcons.invalidatePreview(pile)
        }
        saveState()
        glSurfaceView?.requestRender()
    }

    fun addItemToPile(item: BumpItem, pile: Pile) {
        if (PileOperations.addItemToPile(sceneState, item, pile)) {
            saveState()
        }
    }

    private fun breakPile(pile: Pile) {
        val maxScale = pile.items.maxOfOrNull { it.transform.scale } ?: physicsEngine.defaultScale
        val spacing = maxOf(physicsEngine.gridSpacingBase, maxScale * 2.55f)
        PileOperations.breakPile(sceneState, pile, spacing)
        saveState()
        glSurfaceView?.requestRender()
    }
    fun resetView() {
        AllAppsDrawer.removePile(sceneState)
        collapseNonPinnedPiles()
        sceneState.recentsPile?.reconcilePinnedOpenState()
        val profile = ScreenMetrics.from(context)
        OrientationCameraAnchor.clear(context, profile.orientationKey)
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        if (isFlatFloorMode) {
            val aspect = if (surfaceWidth > 0 && surfaceHeight > 0) {
                surfaceWidth.toFloat() / surfaceHeight
            } else {
                profile.widthPx.toFloat() / profile.heightPx.coerceAtLeast(1)
            }
            val bounds = FlatFloorMode.computeFloorBounds(
                FlatFloorMode.DEFAULT_EYE_Y,
                FlatFloorMode.DEFAULT_EYE_Z,
                FlatFloorMode.DEFAULT_FOV,
                aspect,
                FlatFloorMode.DEFAULT_ZOOM,
            )
            camera.transitionToFlatFloorDefaults(bounds, aspect)
            camera.syncSavedViewFromCurrent()
        } else {
            camera.applyProfileDefaults(profile)
        }
        sessionProfileApplied = true
        (context as? LauncherActivity)?.showResetButton(false)
        glSurfaceView?.requestRender()
    }
    fun dismissExpandedPile() {
        AllAppsDrawer.removePile(sceneState)
        collapseNonPinnedPiles()
        sceneState.recentsPile?.reconcilePinnedOpenState()
        if (isFlatFloorMode) {
            restoreFlatFloorCamera()
        } else {
            camera.restorePreviousView()
        }
        val showReset = !isFlatFloorMode && camera.currentViewMode != CameraManager.ViewMode.DEFAULT
        (context as? LauncherActivity)?.showResetButton(showReset)
        glSurfaceView?.requestRender()
    }
    fun onDisplayProfileChanged() {
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        interactionManager.updateTouchMetrics(context)
        val profile = ScreenMetrics.from(context)
        if (prefs.contains("cam_def_pos_x") && !isFlatFloorMode) {
            CameraDiagnostics.log(
                camera,
                "orientationChange",
                "skipped=savedCustomCamera orientation=${profile.orientationKey} ${profile.widthPx}x${profile.heightPx}"
            )
            glSurfaceView?.requestRender()
            return
        }

        val lastOrientation = prefs.getString(ScreenMetrics.PREFS_LAST_ORIENTATION, null)
        if (lastOrientation != profile.orientationKey) {
            prefs.edit()
                .putString(ScreenMetrics.PREFS_LAST_ORIENTATION, profile.orientationKey)
                .apply()
            applyOrientationProfile(profile, "orientationChange")
            sessionProfileApplied = true
        } else if (!sessionProfileApplied &&
            (camera.currentViewMode == CameraManager.ViewMode.DEFAULT ||
                (isFlatFloorMode && camera.currentViewMode == CameraManager.ViewMode.FLOOR))
        ) {
            applyOrientationProfile(profile, "orientationChange")
            sessionProfileApplied = true
        } else {
            CameraDiagnostics.log(
                camera,
                "orientationChange",
                "unchanged orientation=${profile.orientationKey} ${profile.widthPx}x${profile.heightPx}"
            )
        }
        glSurfaceView?.requestRender()
    }

    fun handleZoom(sf: Float) {
        camera.zoomLevel = (camera.zoomLevel / sf).coerceIn(0.5f, 2.5f)
        if (abs(camera.zoomLevel - camera.customDefaultZoomLevel) > 0.05f) {
            (context as? LauncherActivity)?.showResetButton(true)
        }
    }
    fun handlePan(dx: Float, dy: Float) { camera.handlePan(dx, dy); (context as? LauncherActivity)?.showResetButton(true) }

    fun persistOrientationCameraAnchor(spanDelta: Float, panTotal: Float, zoomProduct: Float) {
        if (camera.currentViewMode != CameraManager.ViewMode.DEFAULT) return
        val profile = ScreenMetrics.from(context)
        OrientationCameraAnchor.save(context, profile.orientationKey, camera)
        camera.customDefaultPos = camera.targetPos.clone()
        camera.customDefaultLookAt = camera.targetLookAt.clone()
        camera.customDefaultZoomLevel = camera.zoomLevel
        CameraDiagnostics.log(
            camera,
            "twoFingerEnd",
            "spanΔ=${"%.1f".format(spanDelta)} panTotal=${"%.0f".format(panTotal)} " +
                "zoomProduct=${"%.3f".format(zoomProduct)} orientation=${profile.orientationKey}"
        )
    }

    private fun applyInfiniteDesktopCamera(profile: ScreenMetrics.DisplayProfile, reason: String) {
        camera.customDefaultPos = profile.defaultCameraPos.clone()
        camera.customDefaultLookAt = profile.defaultCameraLookAt.clone()
        camera.customDefaultZoomLevel = profile.defaultZoomLevel
        camera.baseFieldOfView = profile.defaultFieldOfView
        camera.currentViewMode = CameraManager.ViewMode.DEFAULT
        camera.applyProfileDefaults(profile)
        (context as? LauncherActivity)?.showResetButton(false)
        CameraDiagnostics.log(
            camera,
            reason,
            "source=infiniteDesktopDefaults orientation=${profile.orientationKey}",
        )
    }

    private fun applyOrientationProfile(profile: ScreenMetrics.DisplayProfile, reason: String) {
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        if (isFlatFloorMode) {
            applyFlatFloorBounds(profile)
            val aspect = surfaceWidth.toFloat() / surfaceHeight.coerceAtLeast(1)
            val bounds = FlatFloorMode.computeFloorBounds(
                FlatFloorMode.DEFAULT_EYE_Y,
                FlatFloorMode.DEFAULT_EYE_Z,
                FlatFloorMode.DEFAULT_FOV,
                aspect,
                FlatFloorMode.DEFAULT_ZOOM,
            )
            camera.transitionToFlatFloorDefaults(bounds, aspect)
            CameraDiagnostics.log(
                camera,
                reason,
                "source=flatFloor orientation=${profile.orientationKey} ${profile.widthPx}x${profile.heightPx}"
            )
            return
        }
        if (physicsEngine.isInfiniteMode) {
            applyInfiniteDesktopCamera(profile, reason)
            return
        }
        val anchor = OrientationCameraAnchor.load(context, profile.orientationKey)
        if (anchor != null) {
            camera.applyAnchor(anchor)
            CameraDiagnostics.log(
                camera,
                reason,
                "source=userAnchor orientation=${profile.orientationKey} ${profile.widthPx}x${profile.heightPx}"
            )
        } else {
            camera.applyProfileDefaults(profile)
        }
    }

    private fun folderPanelHalfExtent(pile: Pile): Float =
        (FolderDrawerStyle.panelHalfDimX(pile) + FolderDrawerStyle.panelHalfDimY(pile)) * 0.5f

    private fun applyFlatFloorBounds(profile: ScreenMetrics.DisplayProfile) {
        val aspect = if (surfaceWidth > 0 && surfaceHeight > 0) {
            surfaceWidth.toFloat() / surfaceHeight
        } else {
            profile.widthPx.toFloat() / profile.heightPx.coerceAtLeast(1)
        }
        val bounds = FlatFloorMode.computeFloorBounds(
            FlatFloorMode.DEFAULT_EYE_Y,
            FlatFloorMode.DEFAULT_EYE_Z,
            FlatFloorMode.DEFAULT_FOV,
            aspect,
            FlatFloorMode.DEFAULT_ZOOM,
        )
        floorHalfWidth = bounds.halfX
        floorHalfDepth = bounds.halfZ
        physicsEngine.floorHalfX = bounds.halfX
        physicsEngine.floorHalfZ = bounds.halfZ
        interactionManager.floorHalfX = bounds.halfX
        interactionManager.floorHalfZ = bounds.halfZ
        interactionManager.roomSize = bounds.halfX
        physicsEngine.roomSize = bounds.halfX
        camera.MAX_X = bounds.halfX - 1f
        camera.MAX_Z = bounds.halfZ - 1f
        camera.MIN_X = -bounds.halfX + 1f
        camera.MIN_Z = -bounds.halfZ + 1f
    }

    override fun onSurfaceChanged(unused: GL10, w: Int, h: Int) {
        surfaceWidth = w; surfaceHeight = h
        GLES20.glViewport(0, 0, w, h); interactionManager.screenWidth = w; interactionManager.screenHeight = h;
        Matrix.perspectiveM(projectionMatrix, 0, camera.fieldOfView, w.toFloat() / h, 0.1f, 100f)
        CameraDiagnostics.log(camera, "surfaceChanged", "surface=${w}x${h}")
        if (isFlatFloorMode) {
            applyFlatFloorBounds(ScreenMetrics.from(context))
            constrainWidgetsToActiveBounds(saveIfChanged = true)
        }
        syncWallpaperFloorCrop()
        onDisplayProfileChanged()
    }
}
