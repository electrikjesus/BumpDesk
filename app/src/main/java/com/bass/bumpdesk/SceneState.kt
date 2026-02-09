package com.bass.bumpdesk

import android.appwidget.AppWidgetHostView
import android.webkit.WebView
import java.util.concurrent.CopyOnWriteArrayList

class SceneState {
    val bumpItems = CopyOnWriteArrayList<BumpItem>()
    val widgetItems = CopyOnWriteArrayList<WidgetItem>()
    val widgetViews = mutableMapOf<Int, AppWidgetHostView>()
    val webViews = mutableMapOf<Int, WebView>() // ID to WebView mapping
    val piles = CopyOnWriteArrayList<Pile>()
    
    var recentsPile: Pile? = null
    var appDrawerItem: BumpItem? = null
    val allAppsList = CopyOnWriteArrayList<AppInfo>()

    @Volatile
    var selectedItem: BumpItem? = null
    @Volatile
    var selectedWidget: WidgetItem? = null
    
    fun getPileOf(item: BumpItem) = piles.find { it.items.contains(item) }
    
    fun isAlreadyOnDesktop(app: AppInfo): Boolean {
        return bumpItems.filter { item -> !piles.any { it.items.contains(item) } }
            .any { it.appInfo?.packageName == app.packageName }
    }
}
