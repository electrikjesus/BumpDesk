# BumpDesk Development

## Rules:

- **Document everything**: Maintain `dev_plans.md` for tasks and `docs/architecture.md` for high-level structure.
- **Test-First**: Create or identify a test case *before* implementing a task to define success and prevent regressions.
- **One Task at a Time**: Complete and verify a single task before moving to the next.
- **Granular Steps**: Break complex tasks into 3-4 smaller, verifiable sub-tasks to maintain context and avoid timeouts.
- **Build-After-Edit**: Execute `gradle build` or a specific task-related build/test after *every* edit.
- **Verification**: Only mark a task as complete [x] after the build succeeds AND the verification test passes.
- **Autonomous Progress**: Work independently until a blocker or feedback is required.
- **Accountability Checklist**: Every summary MUST include:
    1. [x] **Code Compiled?** (Actual successful `gradle build` output)
    2. [x] **Logic Verified?** (Unit tests passed)
    3. [x] **Self-Audit Done?** (Checked for redundant methods, leftover debug code, or type mismatches)
    4. [x] **Plan Updated?** (`dev_plans.md` reflects latest state)

# BumpDesk changes

- [x] **Priority**: Fix app size in folder views (Apps in All Icons group/folder are way too large when the folder view is open)
- [x] **Priority**: Fix GitHub Actions and CI stability (LassoSmoothingTest fix)
- [x] **Context Improvement**: Modularize Rendering Logic
- [x] **Context Improvement**: Extract Business Logic from LauncherActivity
- [x] **Context Improvement**: Improve SceneState and Persistence
- [x] **Context Improvement**: Create Architecture Summary
- [x] Fix app icons not being able to drag from folder view.
- [x] Fix app loading to black screen
- [x] Fix stacks not allowing to be dragged and moved
- [x] Rotate app titles, flip folder names, allow dragging apps from folders.
- [x] Fix widget updates (Clock etc)
- [x] Enable Theme Loading
- [x] Transparent Navigation Bar Insets
- [x] Toss icons into piles
- [x] Center widgets on click
- [x] Fix pinch-to-zoom gestures
- [x] Raise items when moving
- [x] Fix widget/icon orientation on side walls
- [x] Orient pinned wall apps correctly
- [x] Group pile dragging visuals
- [x] Prevent stacks from intersecting walls
- [x] Android.bp for AOSP
- [x] Recents Provider Activity & Makefile
- [x] Recents switching modes (Freeform, etc)
- [x] Privileged permissions for Recents
- [x] Fix Recents widget z-fighting lines
- [x] Long-click menu for widgets (Move/Remove)
- [x] Fix Radial Menu secondary layers
- [x] Add normal Android widgets
- [x] Theme Manager Integration
- [x] SoftPile logic from Windows source
- [x] Flatten Recents Widget on floor
- [x] Toss app into stack prompt
- [x] Auto-load theme configs
- [x] Fix Recents widget white panels
- [x] Fix Pile fan-out drag lock
- [x] Fix lasso on centered floor
- [x] Fix Freeform intent flags
- [x] Fix widget orientation on side walls
- [x] Fix widget texturing (white box fix)
- [x] Recents Widget: Fix blank surfaces
- [x] Fix All Apps stack drag lock
- [x] Fix Themes cache clearing
- [x] Fix floor plane texture stretch
- [x] Fix Recents app tile icons
- [x] Recents Widget: shortcut icons
- [x] Recents Widget: Close button
- [x] Fix Grid Layout action
- [x] System Wallpaper as Floor
- [x] Populate Settings Activity
- [x] Lasso on focused floor
- [x] Fix floor wallpaper crash
- [x] Fix theme reload black textures
- [x] Remove floor tile from folders
- [x] Themes fallback path fix
- [x] Replace All Apps stack with Icon
- [x] Camera: restore view after folder close
- [x] Hide icon 3D edges
- [x] Gitignore setup
- [x] Project README
- [x] Live widget updates
- [x] Fix Recents Widget only showing the arrows
- [x] Fix pinch-to-zoom on all views
- [x] Fix theme reload without restart
- [x] Play Store compliance (Onboarding/Permissions)
- [x] Lasso Selection Visuals (Smoothing)
- [x] Sound Effects Integration
- [x] Widget Resizing Logic (Handles and persistence)
- [x] Fix widget interaction stream
- [x] Fix missing All Apps desktop icon
- [x] Update Recents background listener
- [x] Add Show/Hide App Drawer option to Settings
- [x] Auto-Categorization Logic
- [x] Performance Optimization (Batching/VBOs)
- [x] Fix long-press gesture slop
- [x] Fix 2-finger pan lasso trails
- [x] Fix 2-finger tilt gesture
- [x] Add option for infinite desktop mode
- [x] Add Setting Default Launcher to onboarding
- [x] Fix wall-mounted widget resize handles
- [x] Texture & Memory Audit
- [x] Concurrency Management
- [x] Physics Engine Scalability
- [x] Advanced Lasso Menu
- [x] Search/Find Implementation
- [x] Type-Safe Physics (Vector3 Refactor)
- [x] Allow mouse middle click to be used for 2-finger pan/tilt gestures
- [x] **Priority**: Fix rendering issues
- [x] **Priority**: Fix Persistence and Interaction Accuracy
- [x] **Priority**: Fix theme and infinite desktop application delay
- [x] **Priority**: Implement 4x4 paging grid for expanded folders
- [x] **Priority**: Fix Folder and Recents UI issues
- [x] **Priority**: Fix Infinite mode transitions and Camera boundaries
- [x] **Priority**: Fix room geometry and camera constraints
- [x] **Priority**: dynamic room dimensions for dragging interaction
- [x] **Priority**: add long-click menu option to set default camera view as current view
- [x] **Priority**: add scale slider in settings for room size
- [x] **Priority**: fix CameraManagerTest > testZoomLogic FAILED
- [x] **Context Improvement**: use the same signature to compile in Android Studio and GitHub actions
- [x] **Priority**: expand on our recents widget and add logging
- [x] **Priority**: Fix LauncherActivity crash on destroy (Uninitialized renderer)
- [x] **Priority**: Fix release APK compilation failure in CI (Keystore error)
- [x] **Priority**: Fix multi-finger touchscreen gestures
    - [x] 2-finger: Up/Down = Pan Forward/Backward, Left/Right = Pan Left/Right, Pinch = Zoom
    - [x] 3-finger: Up/Down = Tilt Up/Down, Left/Right = Look Left/Right
    - [x] Fix lasso triggering erroneously during multi-finger gestures
- [ ] **Priority**: Global UI and Persistence Sync
    - [ ] Update icon scale from Settings to affect desktop icons immediately
    - [x] Fix icons showing too large in folder view when slider is high (scale them together)
    - [ ] Remember custom default camera positions across restarts (Persistence)
    - [ ] **Task**: Fix item focus scaling (2/3 rule for widgets/folders)
- [ ] **Priority**: Fix floor texture scaling (Incorrect quadrants mapping/stretching)
- [ ] **Priority**: Fix missing "set camera default" right-click/long-click menu option in all context menus
- [ ] **Priority**: Fix camera clipping through front wall
    - [ ] Implement back-face culling for room walls
    - [ ] Ensure front wall is invisible when camera is outside the room
- [x] **Performance**: Implement virtualized AppDrawer loading (paged bitmap generation).
- [x] **UX**: Improve complex widget horizontal/vertical scrolling in 3D projection.
- [ ] **Tactile**: Advanced haptic feedback for room boundary collisions and item interactions.

## Refactoring & Infrastructure (High Impact)

- [x] **Complete**: Component-Based Architecture (Entity Component System Lite)
- [x] **Task**: Resource Lifecycle Audit (Leak Prevention)
- [x] **Task**: Automated Integration Testing (Headless GL verification)

## AI Workflow & Reward System

### Points System (Reward)
- **Goal**: Reach 1,000,000 pts to achieve "Independent Engineering consultant" status.
- **Base Task Completion**: +200 pts.
- **Verification & Testing (Bonus)**: +100 pts (Total +300 per task with tests).
- **Complex Refactoring**: +100 pts.
- **"First-Time Right"**: +100 pts.
- **False Completion Report**: -500 pts.
- **Compilation Error in "Verified" turn**: -500 pts.
- **Logic Regression**: -250 pts.
- **Rule Breach / Forgetting Step**: -250 pts.

Current Points: 12590
Status: Senior Architect
