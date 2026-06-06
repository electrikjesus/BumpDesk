package com.bass.bumpdesk

import android.content.Context

object PileFolderIcons {
    fun signature(pile: Pile): String {
        val icons = pile.items.take(4).joinToString("|") { item ->
            item.appData?.appInfo?.packageName ?: item.appearance.type.name
        }
        return if (pile.showsCollapsedLabel()) {
            "$icons::${if (pile.isRecentsPile()) "Recents" else pile.name}"
        } else {
            icons
        }
    }

    fun ensurePreview(context: Context, pile: Pile, textureManager: TextureManager) {
        if (!pile.showsCollapsedPreview()) return
        val sig = signature(pile)
        if (
            pile.previewTextureId != -1 &&
            pile.previewSignature == sig &&
            textureManager.isActive(pile.previewTextureId)
        ) {
            return
        }

        val iconBitmaps = pile.items.take(4).mapNotNull { item ->
            val appInfo = item.appData?.appInfo ?: return@mapNotNull null
            if (pile.isRecentsPile()) {
                appInfo.snapshot?.let { snap ->
                    TextureUtils.centerCropToAspect(snap, 1f, 1f)
                } ?: appInfo.icon?.let { TextureUtils.getBitmapFromDrawable(it) }
            } else {
                val override = ThemeManager.getIconOverride(context, appInfo.packageName)
                override ?: appInfo.icon?.let { TextureUtils.getBitmapFromDrawable(it) }
            }
        }
        if (iconBitmaps.isEmpty()) return

        val label = when {
            pile.showsFolderLabel() -> pile.name
            pile.isRecentsPile() -> "Recents"
            else -> null
        }
        val combined = TextureUtils.createPileFolderIcon(iconBitmaps, label = label)
        pile.previewTextureId = textureManager.loadTextureFromBitmap(combined)
        pile.previewSignature = sig
        combined.recycle()
        iconBitmaps.forEach { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    fun invalidatePreview(pile: Pile) {
        pile.previewTextureId = -1
        pile.previewSignature = ""
    }
}
