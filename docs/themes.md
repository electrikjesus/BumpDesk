# BumpDesk Theme Structure

BumpDesk themes are stored in `app/src/main/assets/BumpTop/`. Each theme is a directory containing a `theme.json` configuration file and several asset subdirectories.

## Directory Overview

- `theme.json`: Main configuration file (Colors, Fonts, Asset Mapping, Shaders).
- `core/`: Core UI elements.
    - `icon/`: Overlays (shortcuts), selection masks.
    - `pile/`: Backgrounds for stacks/piles.
- `desktop/`: Room textures.
    - `floor.svg` / `floor_desktop.jpg`: Floor texture.
    - `wall.svg` / `wall.png`: Wall textures.
- `widgets/`: Assets for 3D widgets (Sticky notes, Web, Photo Frames).
    - `close.svg`, `scrollUp.svg`, `scrollDown.svg`.
- `override/`: Custom icons for specific file extensions or system types.
    - `folder.svg`, `doc.svg`, `pdf.svg`, `txt.svg`, `camera.svg`, etc.
- `slideshow/`: Navigation for Photo Frames.
    - `next.svg`, `previous.svg`.
- `environment.frag`: Custom fragment shader for procedural effects (e.g., caustics).

## SVG vs Raster
BumpDesk prefers `.svg` files for high-fidelity rendering on high-DPI screens. If an `.svg` version of an asset exists, the engine will prioritize it over `.png` or `.jpg`.

## theme.json Schema

### Header
```json
"header" : {
    "name" : "Theme Name",
    "description" : "Short description",
    "author" : "Author Name",
    "animated": true // Enables procedural shaders and continuous rendering
}
```

### UI (Colors & Fonts)
Colors are defined as `[R, G, B, A]` where values are 0-255.
- `ui.icon.highlight`: Selection and hover colors.
- `ui.icon.font`: Label text styling.
- `ui.markingMenu`: Radial menu colors.

### Textures
Maps keys to filenames within the theme subdirectories.
```json
"textures" : {
    "floor" : { "desktop" : "floor.svg" },
    "wall" : { "top" : "wall.svg", "bottom": "wall.svg", "left": "wall.svg", "right": "wall.svg" }
}
```

### Shaders
Optional block to define custom shaders for the theme.
```json
"shaders": {
    "environment": "environment.frag"
}
```
The `environment.frag` file should contain a `void applyEnvironment(inout vec4 color, vec3 pos, vec3 normal, float time)` function that will be called by the engine for background surfaces when `animated` is true.
