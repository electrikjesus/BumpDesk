package com.bass.bumpdesk

import android.content.Context

object PileFolderIcons {
    fun signature(pile: Pile): String {
        val icons = pile.items.take(4).joinToString("|") { item ->
            item.appData?.appInfo?.packageName ?: item.appearance.type.name
        }
        return if (pile.showsFolderLabel()) "$icons::${pile.name}" else icons
    }

    fun ensurePreview(context: Context, pile: Pile, textureManager: TextureManager) {
        if (!pile.showsFolderPreview()) return
        val sig = signature(pile)
        if (pile.previewTextureId != -1 && pile.previewSignature == sig) return

        val iconBitmaps = pile.items.take(4).mapNotNull { item ->
            val appInfo = item.appData?.appInfo ?: return@mapNotNull null
            val override = ThemeManager.getIconOverride(context, appInfo.packageName)
            override ?: appInfo.icon?.let { TextureUtils.getBitmapFromDrawable(it) }
        }
        if (iconBitmaps.isEmpty()) return

        val label = if (pile.showsFolderLabel()) pile.name else null
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
