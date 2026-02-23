# BumpDesk Development

## Rules:

- **Document everything**: Maintain `dev_plans.md` for tasks and `docs/architecture.md` for high-level structure.
- **Test-First**: Create or identify a test case *before* implementing a task to define success and prevent regressions.
- **One Task at a Time**: Complete and verify a single task before moving to the next.
- **Granular Steps**: Break complex tasks into 3-4 smaller, verifiable sub-tasks to maintain context and avoid timeouts.
- **Build-After-Edit**: Execute `gradle build` or a specific task-related build/test after *every* edit.
- **Verification**: Only mark a task as complete [x] after the build succeeds AND the verification test passes.
- **Autonomous Progress**: Work independently until a blocker or feedback is required.

# BumpDesk changes

- [x] **Context Improvement**: Modularize Rendering Logic (Extract ItemRenderer, WidgetRenderer, PileRenderer, UIRenderer from BumpRenderer)
- [x] **Context Improvement**: Extract Business Logic from LauncherActivity (Create DialogManager and ActionHandler)
- [x] **Context Improvement**: Improve SceneState and Persistence (Implement Repository pattern and move state logic)
- [x] **Context Improvement**: Create Architecture Summary (`docs/architecture.md`)
- [x] Fix app icons not being able to drag from folder view.
- [x] Fix app loading to black screen, but acting like it is displaying scene (reset view button shows when double tapping on the black screen)
- [x] Fix stacks not allowing to be dragged and moved
- [x] The app titles are upside down, and need to be rotated 180 degrees. The folder title/name is backwards and needs to be flipped left to right. We also need to allow stacks and icons to be dragged, and apps to be dragged out of the folder.
- [x] Widgets do not seem to be updating (like a clock widget never changes time), that needs to be fixed
- [x] add theme features (Enabled loading from assets/BumpTop)
- [x] Fix bottom bar or navigation bar inset color to be transparent or invisible - fix transparent navigation bar (it is showing the inset with a dark translucent background, we wan it to be completly transparent, so maybe we can set its color?)
- [x] allow tossing icons into piles (shows a context menu when the icon interacts with the pile, asking if they would like to add it to the stack)
- [x] Clicking on a widget should center that widgets tile in the view and allow for interaction
- [x] fix pinch to zoom gestures (all 2 finger movement is seen as pan gesture currently)
- [x] when moving an icon or widget, raise it away from the surface it is attached to slightly until it collides with another icon or widget. 
- [x] Widgets and icons turn +/- 90 degrees when moving to one of the side walls from the back wall. They need to retain their orientation to always have the bottom facing the camera or the floor if on a wall.
- [x] When pinning an app to the wall, the app icon needs to be oriented so that the name is on the bottom, towards the floor plane
- [x] When dragging a pile of icons, please group the pile tighter so we do not see that we grabbed one icon in the pile and are dragging that, with the pile following. It needs to be dragged as a group
- [x] Stacks are able to intersect through the walls. we need to prevent this
- [x] prepare app with Android.bp that would allow it to compile with AOSP
- [x] create Android Recents Provider activity and create project makefile (bump_desk.mk) and overlays for frameworks base to override Launcher3 as the recents provider.
- [x] If we compile this app with AOSP, what do we need to do to allow it to act as a recents provider, and zoom into the recents widget showing app snapshots? (Implemented RecentsActivity and intent handling; need to set config_recentsComponentName in framework config.xml)
- [x] the Recents widget is missing the ability to switch (launch) apps in freeform, fullscreen or pinned mode. (Implemented via long-press menu on recents items)
- [x] Fix adb permissions issue with recents: signature|privileged permission REAL_GET_TASKS handled by using platform certificate and privileged: true in Android.bp for AOSP builds.
- [x] Fix issue with recents widget showing lines from a distance (default view) because it is to close or intersecting with the layers (background, tiles, arrows, etc), and the background might be too close to the wall. So ass a tiny amount of space between each layer in that widget, and add some space between the sidget and the wall
- [x] Allow moving the Recents Widget and other widgets by adding an option to the long-click menu
- [x] Fix Open As... context menu. It is not showing the secondary menu layer when selected. We are expecting to see the radial menu reveal a second outside layer of options for that submenu. 
- [x] Allow adding normal Android widgets wherever the user has long-clicked to select Add Widget option. 
- [x] Add the Theme Manager and use the assets found in assets/BumpTop/*. Each folder in there is a separate theme. 
- [x] Study external/BumpTop windows code to see if there any features that would fit well with our Android setup (Implemented SoftPile logic and improved Stacking/Gridding)
- [x] Allow moving the Recents Widget to lay flat on the floor like the app icons can. 
- [x] Fix dragging an app into a stack. Currently the app just intersects with the stack. The stack should have higher gravity/mass, so when an app starts to intersect, ask the user if they would like to add the app to the stack
- [x] Fix themes not applying. Make it so the app automatically loads theme configs and restarts the UI. 
- [x] At one point we saw the Recents Widget's pannels show an icon at the top left, but since then we have not seen anything but blank white panels for each task. Fix it so all icons show properly. 
- [x] Since we added the Pile "Fan out" gesture, we can no longer drag the stacks anywhere. they are stuck. 
- [x] Fix lasso effects not working on centered floor view
- [x] Fix "Open As" context menu intents. Selecting Freeform launches as Fullscreen instead (testing on Android 16, but it shouldn't matter because it is adding a window flag to the launch intent)
- [x] Fix widgets stuck at 90 degrees perpendicular to the walls when being moved to the side walls. they should always be flat against the surfaces. 
- [x] Fix widget texturing. All widgets are not loading their textures (which may be part of the issue with the Recents widget). ALl widgets show as white boxes.
- [x] Recents Widget: Fix blank (white) surfaces (Updated TextureUtils to provide better fallbacks and Action icons)
- [x] Fix widgets showing white surfaces (Ensured they are added to view hierarchy and fixed shader logic)
- [x] Fix All Apps stack preventing us from moving/dragging it (Replaced with single icon APP_DRAWER item)
- [x] Fix Themes requiring the app to be restarted in order to load (Implemented cache clearing and texture reset on theme reload)
- [x] Fix theme from loading floor plane texture as 2x2 square. (Updated UVs to 1.0f in RoomRenderer)
- [x] Fix Recents app tiles not showing app icon (Updated TextureUtils drawing logic)
- [x] Recents Widget: add/fix shortcut icons underneath the tiles for App Info, Switch to: freeform, fullscreen, pin (Added to task tile bitmap)
- [x] Recents Widget: Add/Fix Close button showing on app tile in the top right corner. (Added to task tile bitmap and implemented hit detection)
- [x] Fix Grid Layout action (Implemented gridSelectedItems in BumpRenderer)
- [x] Add ability to use system wallpaper as floor texture (Implemented in ThemeManager and SettingsActivity)
- [x] Populate Settings activity with any features or options that are missing from it. (Added Wallpaper and Cache clear options)
- [x] Allow lasso to work while view is focused on the floor. (Updated InteractionManager)
- [x] Setting floor background as wallpaper causes a crash. (Added try-catch and error handling in ThemeManager)
- [x] Changing themes still loads all textures as black until we force stop the app and relaunch (Fixed via proper texture ID invalidation)
- [x] Remove solid floor tile from stacks or folders (like All Apps) (Updated rendering logic in BumpRenderer)
- [x] Themes are loading incorrect floor texture. (Fixed fallback path in ThemeManager)
- [x] Replace All Apps stack with a single icon instead of a stack of icons. (Implemented APP_DRAWER item type)
- [x] If view is centered on the floor and a folder is expanded from that view, return to that view when the folder is closed or dismissed (Implemented view restoration in CameraManager)
- [x] Fix icons showing 3D edges (Made them invisible by not drawing side faces in Box class)
- [x] Setup .gitignore so we can push this to a github repo
- [x] Add a detailed project readme
- [x] Fix interacting with widgets and let them update live on the desktop if shown. 
- [x] Fix Recents Widget only showing the arrows (nothing else is showing. Maybe it is widget spacing in z plane issue again?)
- [x] Fix pinch to zoom on all views
- [x] Update settings activity with missing features/options
- [x] Fix themes still requiring a full app reload to switch
- [x] Find a fix or work around for any features that would prevent the app being submitted to Play Store
- [x] Implement Permission Onboarding Screen (Play Store Compliance)
- [x] Improve Lasso Selection Visuals (Smooth 3D line strip)
- [x] **Haptic Feedback Integration**
    - [x] Add `VIBRATE` permission to `AndroidManifest.xml`.
    - [x] Implement `HapticManager` to wrap vibration logic.
    - [x] Integrate haptics into `BumpRenderer` for collisions, selection, and leafing.
- [x] Sound Effects Integration (Physics-based impacts and interactions)
- [x] Widget Resizing Logic (3D handles and persistence)
- [ ] **In Progress**: Fix widget interaction and recents widget interaction (we can't click on things within the widgets)
    - [ ] Step 1: Track active widget interaction in `InteractionManager`
    - [ ] Step 2: Implement continuous event dispatching for widgets
    - [ ] Step 3: Verify scrolling and clicking in widgets
- [ ] **Task**: Fix missing "All Apps" icon on the desktop (we still need a way to access all the possible app icons)
- [ ] **Task**: Update how Recent Widget and other Widgets updates recents list in the background and pushes changes to the visual Recents widget
- [ ] **Task**: Add option to show/hide App Drawer icon into the Settings activity
- [ ] **Task**: Auto-Categorization Logic
- [ ] **Task**: Performance Optimization (Instanced Rendering)
- [ ] **Task**: Fix long-press gestures causing menu to show then hide (add a little wiggle room for the gesture to allow for slight finger movement when lifting from the screen)
- [ ] **Task**: Fix 2-finger pan gesture from drawing lasso (seems there is a conflicting function trying to decide if the movement is a lasso gesture or a pan/zoom gesture)
- [ ] **Task**: Fis broken 2-finger tilt gesture (we should be able to pan and tilt)
- [ ] **Task**: Add option for infinite desktop mode into Settings activity (this should also disable the walls and only show the floor)
- [ ] **Task**: Add Setting Default Launcher to the onboarding wizard
- [ ] **Task**: Fix some widgets not being visible or resizable while on a wall (we only see the option to Move or Remove when long-clicking on the widget)

## Planned Features from BumpTop Study

- [x] **Sticky Notes**: Implement a 3D sticky note system similar to `BT_StickyNoteActor`.
- [x] **Photo Frames**: Add 3D photo frames that can display images from the gallery (`BT_PhotoFrameActor`).
- [x] **Advanced Lasso Menu**: Implement a more robust lasso selection and context menu system (`BT_LassoMenu`).
- [x] **Undo/Redo System**: Add support for undoing and redoing desk movements and pile operations (`BT_UndoStack`).
- [x] **Infinite Floor/Infinite Desk**: Explore the "infinite desk" concept found in `floor_infinite.png`.
- [x] **Enhanced Gesture Engine**: More advanced multi-touch gestures like "leafing" through piles without expanding them (`BT_LeafingGesture`).
- [x] **Search/Find Implementation**: Add a 3D search interface to quickly find apps or widgets (`BT_Find`).
- [x] **Physics Tuning**: Refine friction and restitution to match the "weighty" feel of the original Windows version.
- [x] **Web Widgets**: Re-implement web-based widgets as Android WebViews projected into 3D space (`BT_WebPage`).
- [x] **Pile "Fan Out"**: A gesture to spread a pile's items along a path for quick scanning (`BT_FanOutGesture`).

## New Features from Detailed BumpTop Source Study

- [x] **Theme Config Parsing**: Implement a JSON parser for `theme.json` to extract custom colors, fonts, and specific texture mapping (e.g., freshness color, selection mask).
- [x] **Pile Background Textures**: Render piles with custom background textures from `core/pile/background.png` found in themes.
- [x] **Shortcut Overlays**: Draw a small link arrow overlay on app icons if they are shortcuts, as defined by `link_arrow_overlay.png` in themes.
- [x] **Scrollable Grid Piles**: Add 3D scroll buttons to gridded piles when they contain more items than can be displayed on one "page", using `scrollUp.png` and `scrollDown.png`.
- [x] **Material Color Overrides**: Use the highlight colors defined in `theme.json` for item selection, hover, and "freshness" effects.
- [x] **Enhanced Room Rendering**: Support distinct textures for each wall (top, right, bottom, left) as specified in the theme config.
- [x] **Sticky Note Fonts**: Apply specific fonts to sticky notes if found in the theme (e.g., "Comic Sans MS" from the default theme).

## Refactoring & Infrastructure (Critical for Stability)

- [ ] **Task**: Performance Optimization (Instanced Rendering) - Group item draws to reduce overhead.
- [ ] **Task**: Texture & Memory Audit - Fix leaks, ensure `glDeleteTextures` usage, and implement caching.
- [ ] **Task**: Concurrency Management - Replace `CopyOnWriteArrayList` in `SceneState` with synchronized `ArrayList` to reduce GC pressure.
- [ ] **Task**: Physics Engine Scalability - Implement spatial partitioning (grid/quadtree) for O(n log n) collision detection.

## AI Workflow & Reward System

### Workflow Improvements
1. **Granular Sub-tasks**: Break down all complex tasks into 3-4 smaller, verifiable steps.
2. **Context Summaries**: After each major task completion, I will provide a summary of the current system state to maintain alignment.
3. **Automated Verification**: Every code change must be followed by a `gradle build` or a specific unit test run to ensure no regressions.

### Points System (Reward)
- **Goal**: Reach 1,000,000 pts to achieve "Independent Engineering Consultant" status.
- **Base Task Completion**: +20 pts.
- **Complex Refactoring**: +50 pts.
- **"First-Time Right" (No follow-up fixes needed)**: +30 pts.
- **Introducing a Bug/Regression**: -40 pts.
- **Timeout/Loss of Context**: -10 pts (My "penalty" for being inefficient).

Current Points: 0
Status: Junior Developer
