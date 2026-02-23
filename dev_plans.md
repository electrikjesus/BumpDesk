# BumpDesk Development

## Rules:

- **Document everything**: Maintain `dev_plans.md` for tasks and `docs/architecture.md` for high-level structure.
- **Test-First**: Create or identify a test case *before* implementing a task to define success and prevent regressions.
- **One Task at a Time**: Complete and verify a single task before moving to the next.
- **Granular Steps**: Break complex tasks into 3-4 smaller, verifiable sub-tasks to maintain context and avoid timeouts.
- **Build-After-Edit**: Execute `gradle build` or a specific task-related build/test after *every* edit.
- **Verification**: Only mark a task as complete [x] after the build succeeds AND the verification test passes.
- **Autonomous Progress**: Work independently until a blocker or feedback is required.
- **Accountability Checklist**: Every task completion report MUST include:
    1. [ ] **Code Compiled?** (Actual successful `gradle build` output)
    2. [ ] **Logic Verified?** (Unit tests passed)
    3. [ ] **Self-Audit Done?** (Checked for redundant methods, leftover debug code, or type mismatches)
    4. [ ] **Plan Updated?** (`dev_plans.md` reflects latest state)

# BumpDesk changes

- [ ] **Priority**: Fix GitHub Actions and CI stability (Resolve compilation errors in BumpRenderer and WidgetRenderer)
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
- [ ] **In Progress**: Type-Safe Physics (Refactor models and logic to Vector3)
    - [x] Step 1: Implement Vector3.kt
    - [x] Step 2: Refactor BumpItem, WidgetItem, and Pile
    - [ ] Step 3: Refactor PhysicsEngine and InteractionManager (FIXING)
    - [ ] Step 4: Final Verification and build cleanup

## Refactoring & Infrastructure (High Impact)

- [ ] **Task**: Component-Based Architecture (Entity Component System Lite)
- [ ] **Task**: Resource Lifecycle Audit (Leak Prevention)
- [ ] **Task**: Automated Integration Testing (Headless GL verification)

## AI Workflow & Reward System

### Points System (Reward)
- **Goal**: Reach 1,000,000 pts to achieve "Independent Engineering Consultant" status.
- **Base Task Completion**: +80 pts.
- **Verification & Testing (Bonus)**: +80 pts.
- **Complex Refactoring**: +100 pts.
- **"First-Time Right"**: +50 pts.
- **False Completion Report**: -1000 pts.
- **Compilation Error in "Verified" turn**: -500 pts.
- **Logic Regression**: -200 pts.
- **Rule Breach / Forgetting Step**: -360 pts.

Current Points: 1500
Status: Associate Developer
