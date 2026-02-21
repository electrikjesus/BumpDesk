package com.bass.bumpdesk

import android.app.AlertDialog
import android.content.Context
import android.opengl.GLSurfaceView
import android.widget.EditText

class DialogManager(private val context: Context, private val glSurfaceView: GLSurfaceView, private val renderer: BumpRenderer) {

    fun showAddToPileMenu(item: BumpItem, pile: Pile) {
        AlertDialog.Builder(context)
            .setTitle("Add to Stack?")
            .setMessage("Do you want to add ${item.appInfo?.label ?: "this item"} to ${pile.name}?")
            .setPositiveButton("Yes") { _, _ ->
                glSurfaceView.queueEvent {
                    renderer.sceneState.bumpItems.remove(item)
                    pile.items.add(item)
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    fun promptAddStickyNote(x: Float, y: Float) {
        val input = EditText(context)
        AlertDialog.Builder(context)
            .setTitle("Add Sticky Note")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    glSurfaceView.queueEvent { renderer.addStickyNote(text, x, y) }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun promptEditStickyNote(item: BumpItem) {
        val input = EditText(context).apply { setText(item.text) }
        AlertDialog.Builder(context)
            .setTitle("Edit Note")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val text = input.text.toString()
                glSurfaceView.queueEvent {
                    item.text = text
                    item.textureId = -1
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun promptAddWebWidget(x: Float, y: Float) {
        val input = EditText(context).apply { setText("https://") }
        AlertDialog.Builder(context)
            .setTitle("Add Web Widget")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val url = input.text.toString()
                if (url.isNotBlank()) {
                    glSurfaceView.queueEvent { renderer.addWebWidget(url, x, y) }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun promptEditWebWidget(item: BumpItem) {
        val input = EditText(context).apply { setText(item.text) }
        AlertDialog.Builder(context)
            .setTitle("Edit URL")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val url = input.text.toString()
                glSurfaceView.queueEvent {
                    item.text = url
                    item.textureId = -1
                    renderer.sceneState.webViews.remove(item.hashCode())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun promptSearch() {
        val input = EditText(context)
        AlertDialog.Builder(context)
            .setTitle("Search Desk")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString()
                glSurfaceView.queueEvent { renderer.performSearch(query) }
            }
            .setNegativeButton("Clear") { _, _ ->
                glSurfaceView.queueEvent { renderer.performSearch("") }
            }
            .show()
    }

    fun promptRenamePile(pile: Pile, onRenamed: (String) -> Unit) {
        val input = EditText(context).apply { setText(pile.name); setSelection(pile.name.length) }
        AlertDialog.Builder(context).setTitle("Rename Folder").setView(input).setPositiveButton("OK") { _, _ ->
            val newName = input.text.toString()
            if (newName.isNotBlank()) glSurfaceView.queueEvent { onRenamed(newName) }
        }.setNegativeButton("Cancel", null).show()
    }
}
