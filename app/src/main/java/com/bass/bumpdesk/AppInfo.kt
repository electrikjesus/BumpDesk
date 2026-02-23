package com.bass.bumpdesk

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    var snapshot: Bitmap? = null,
    var category: Category = Category.OTHER,
    var className: String? = null
) {
    enum class Category {
        GAME, SOCIAL, COMMUNICATION, PRODUCTIVITY, TOOLS, MULTIMEDIA, NAVIGATION, NEWS, OTHER
    }
}
