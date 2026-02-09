package com.bass.bumpdesk

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable? = null,
    var snapshot: Bitmap? = null
)
