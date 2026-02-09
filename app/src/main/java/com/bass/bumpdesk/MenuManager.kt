package com.bass.bumpdesk

import android.content.Context
import android.content.Intent
import android.opengl.GLSurfaceView
import android.provider.Settings
import android.view.View

class MenuManager(
    private val context: Context,
    private val radialMenu: RadialMenuView,
    private val glSurfaceView: GLSurfaceView,
    private val renderer: BumpRenderer,
    private val launcher: LauncherActivity
) {
    fun showItemMenu(x: Float, y: Float, item: BumpItem) {
        val menuItems = mutableListOf<RadialMenuItem>()
        
        if (item.type == BumpItem.Type.APP) {
            menuItems.add(RadialMenuItem("Open", android.R.drawable.ic_menu_send) {
                launcher.launchApp(item)
            })
            
            val openAsSubItems = listOf(
                RadialMenuItem("Fullscreen", android.R.drawable.ic_menu_zoom) {
                    launcher.launchApp(item, LauncherActivity.WINDOWING_MODE_FULLSCREEN)
                },
                RadialMenuItem("Freeform", android.R.drawable.ic_menu_crop) {
                    launcher.launchApp(item, LauncherActivity.WINDOWING_MODE_FREEFORM)
                },
                RadialMenuItem("Pinned", android.R.drawable.ic_lock_lock) {
                    launcher.launchApp(item, LauncherActivity.WINDOWING_MODE_PINNED)
                }
            )
            menuItems.add(RadialMenuItem("Open As...", android.R.drawable.ic_menu_more, subItems = openAsSubItems))
        } else if (item.type == BumpItem.Type.STICKY_NOTE) {
            menuItems.add(RadialMenuItem("Edit", android.R.drawable.ic_menu_edit) {
                launcher.promptEditStickyNote(item)
            })
        } else if (item.type == BumpItem.Type.PHOTO_FRAME) {
            menuItems.add(RadialMenuItem("Change Photo", android.R.drawable.ic_menu_gallery) {
                launcher.promptChangePhoto(item)
            })
        } else if (item.type == BumpItem.Type.WEB_WIDGET) {
            menuItems.add(RadialMenuItem("Edit URL", android.R.drawable.ic_menu_edit) {
                launcher.promptEditWebWidget(item)
            })
        }
        
        val pinText = if (item.isPinned) "Unpin" else "Pin"
        val pinIcon = if (item.isPinned) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_menu_mylocation
        menuItems.add(RadialMenuItem(pinText, pinIcon) {
            glSurfaceView.queueEvent { renderer.togglePin(item) }
        })

        menuItems.add(RadialMenuItem("Grow", android.R.drawable.ic_input_add) {
            glSurfaceView.queueEvent { item.scale = (item.scale * 1.25f).coerceAtMost(2.0f) }
        })

        menuItems.add(RadialMenuItem("Shrink", android.R.drawable.ic_input_delete) {
            glSurfaceView.queueEvent { item.scale = (item.scale / 1.25f).coerceAtLeast(0.2f) }
        })

        if (item.type == BumpItem.Type.APP) {
            menuItems.add(RadialMenuItem("App Info", android.R.drawable.ic_menu_info_details) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.fromParts("package", item.appInfo?.packageName ?: "", null)
                context.startActivity(intent)
            })
        }
        
        menuItems.add(RadialMenuItem("Delete", android.R.drawable.ic_menu_delete) {
            glSurfaceView.queueEvent { renderer.sceneState.bumpItems.remove(item) }
        })
        
        radialMenu.setItems(menuItems, x, y, { it.action?.invoke() }, {})
    }

    fun showPileMenu(x: Float, y: Float, pile: Pile, onBreak: () -> Unit) {
        val menuItems = mutableListOf<RadialMenuItem>()
        menuItems.add(RadialMenuItem("Expand", android.R.drawable.ic_menu_view) {
            glSurfaceView.queueEvent { renderer.handleSingleTap(x, y) }
        })
        
        val fanText = if (pile.isFannedOut) "Collapse" else "Fan Out"
        menuItems.add(RadialMenuItem(fanText, android.R.drawable.ic_menu_sort_alphabetically) {
            glSurfaceView.queueEvent { pile.isFannedOut = !pile.isFannedOut }
        })

        menuItems.add(RadialMenuItem("Move", android.R.drawable.ic_menu_mylocation) {
            // Logic handled by InteractionManager
        })

        menuItems.add(RadialMenuItem("Grow", android.R.drawable.ic_input_add) {
            glSurfaceView.queueEvent { pile.scale = (pile.scale * 1.25f).coerceAtMost(3.0f) }
        })

        menuItems.add(RadialMenuItem("Shrink", android.R.drawable.ic_input_delete) {
            glSurfaceView.queueEvent { pile.scale = (pile.scale / 1.25f).coerceAtLeast(0.5f) }
        })

        menuItems.add(RadialMenuItem("Break Apart", android.R.drawable.ic_menu_delete) {
            glSurfaceView.queueEvent { onBreak() }
        })
        
        radialMenu.setItems(menuItems, x, y, { it.action?.invoke() }, {})
    }

    fun showWidgetMenu(x: Float, y: Float, widget: WidgetItem) {
        val menuItems = mutableListOf<RadialMenuItem>()
        menuItems.add(RadialMenuItem("Move", android.R.drawable.ic_menu_mylocation) {
            // Already handled by being the selectedWidget in InteractionManager
        })
        menuItems.add(RadialMenuItem("Remove", android.R.drawable.ic_menu_delete) {
            glSurfaceView.queueEvent { renderer.removeWidget(widget) }
        })
        radialMenu.setItems(menuItems, x, y, { it.action?.invoke() }, {})
    }

    fun showDesktopMenu(x: Float, y: Float) {
        val menuItems = mutableListOf<RadialMenuItem>()
        menuItems.add(RadialMenuItem("Add Widget", android.R.drawable.ic_menu_add) {
            launcher.saveLastTouchPosition(x, y)
            launcher.openWidgetPicker()
        })
        menuItems.add(RadialMenuItem("Add Note", android.R.drawable.ic_menu_edit) {
            launcher.promptAddStickyNote(x, y)
        })
        menuItems.add(RadialMenuItem("Add Frame", android.R.drawable.ic_menu_gallery) {
            launcher.promptAddPhotoFrame(x, y)
        })
        menuItems.add(RadialMenuItem("Add Web", android.R.drawable.ic_menu_share) {
            launcher.promptAddWebWidget(x, y)
        })
        
        // Undo / Redo
        menuItems.add(RadialMenuItem("Undo", android.R.drawable.ic_menu_revert) {
            glSurfaceView.queueEvent { renderer.interactionManager.undo() }
        })
        menuItems.add(RadialMenuItem("Redo", android.R.drawable.ic_menu_revert) { 
            glSurfaceView.queueEvent { renderer.interactionManager.redo() }
        })

        // Search Implementation
        menuItems.add(RadialMenuItem("Search", android.R.drawable.ic_menu_search) {
            launcher.promptSearch()
        })

        menuItems.add(RadialMenuItem("Settings", android.R.drawable.ic_menu_preferences) {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        })
        radialMenu.setItems(menuItems, x, y, { it.action?.invoke() }, {})
    }

    fun showLassoMenu(x: Float, y: Float, selectedItems: List<BumpItem>) {
        val menuItems = mutableListOf<RadialMenuItem>()
        menuItems.add(RadialMenuItem("Create Pile", android.R.drawable.ic_menu_add) {
            glSurfaceView.queueEvent { launcher.createPileFromCaptured(selectedItems) }
        })
        
        val gridSubItems = listOf(
            RadialMenuItem("Grid", android.R.drawable.ic_menu_sort_by_size) {
                glSurfaceView.queueEvent { renderer.gridSelectedItems(selectedItems, BumpRenderer.GridLayout.GRID) }
            },
            RadialMenuItem("Row", android.R.drawable.ic_menu_sort_alphabetically) {
                glSurfaceView.queueEvent { renderer.gridSelectedItems(selectedItems, BumpRenderer.GridLayout.ROW) }
            },
            RadialMenuItem("Column", android.R.drawable.ic_menu_sort_alphabetically) {
                glSurfaceView.queueEvent { renderer.gridSelectedItems(selectedItems, BumpRenderer.GridLayout.COLUMN) }
            }
        )
        menuItems.add(RadialMenuItem("Layout", android.R.drawable.ic_menu_sort_by_size, subItems = gridSubItems))

        menuItems.add(RadialMenuItem("Delete All", android.R.drawable.ic_menu_delete) {
            glSurfaceView.queueEvent { renderer.sceneState.bumpItems.removeAll(selectedItems) }
        })
        radialMenu.setItems(menuItems, x, y, { it.action?.invoke() }, {})
    }
}
