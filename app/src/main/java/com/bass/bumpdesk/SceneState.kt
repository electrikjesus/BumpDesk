package com.bass.bumpdesk

import android.appwidget.AppWidgetHostView
import android.webkit.WebView
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class SceneState {
    private val lock = ReentrantReadWriteLock()

    // Internal lists using standard ArrayList for performance
    private val _bumpItems = mutableListOf<BumpItem>()
    private val _widgetItems = mutableListOf<WidgetItem>()
    private val _piles = mutableListOf<Pile>()
    
    val widgetViews = mutableMapOf<Int, AppWidgetHostView>()
    val webViews = mutableMapOf<Int, WebView>()
    val allAppsList = mutableListOf<AppInfo>()

    var recentsPile: Pile? = null
    var appDrawerItem: BumpItem? = null

    @Volatile
    var selectedItem: BumpItem? = null
    @Volatile
    var selectedWidget: WidgetItem? = null

    // Thread-safe accessors
    val bumpItems: MutableList<BumpItem> get() = _bumpItems
    val widgetItems: MutableList<WidgetItem> get() = _widgetItems
    val piles: MutableList<Pile> get() = _piles

    fun withReadLock(action: () -> Unit) = lock.read { action() }
    fun <T> withReadLockResult(action: () -> T): T = lock.read { action() }
    fun withWriteLock(action: () -> Unit) = lock.write { action() }

    fun getPileOf(item: BumpItem): Pile? = lock.read {
        return _piles.find { it.items.contains(item) }
    }
    
    fun isAlreadyOnDesktop(app: AppInfo): Boolean = lock.read {
        return _bumpItems.filter { item -> !_piles.any { it.items.contains(item) } }
            .any { it.appInfo?.packageName == app.packageName }
    }

    // Helper for batch processing items safely
    fun forEachBumpItem(action: (BumpItem) -> Unit) = lock.read {
        _bumpItems.forEach(action)
    }

    fun forEachPile(action: (Pile) -> Unit) = lock.read {
        _piles.forEach(action)
    }
}
