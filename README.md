# Dodeclusters ֍
[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
![Static Badge](https://img.shields.io/badge/status-beta-violet)
[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/pier-bezuhoff/Dodeclusters/build.yml?branch=master&event=push)](https://github.com/pier-bezuhoff/Dodeclusters/actions)  

### Circle-based vector graphics editor

![Dodeclusters](docs/02inf-art.png)

## Features
♾️ Infinite canvas, infinite zoom, infinite precision<sup>\*</sup>  
⬤ Powerful circle geometry instrumentation: treating points, circles and straight lines uniformly  
💬 Simple & intuitive design with interactive tips & brief descriptions  
💯 Cross-platform (mobile + desktop + browser)  
🌲 Dynamic expression-dependency tree  
📜 Undo/redo history & auto-save  
💾 Custom YAML-derived file format + Safe SVG export  
✌ Smart gestures: drag-and-drop, swipe coloring & swipe selection, keyboard shortcuts  
🌘 Light/dark mode (*WIP*, for now as a URL argument `?theme=light` or `?theme=dark`)  

\*: objects are related via dynamic expressions and child nodes' formulae are recalculated when moving parents or zooming in/out

Built with [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform), targeting
- [x] Desktop (Windows, Linux)
- [x] Android 7.0+ (SDK 24+)
- [x] Web (via [Wasm](#Web-Wasm-compatibility))
- [ ] macOS/iOS if ever get an Apple dev account (have to notarize/staple binaries), for now 
  only works as a web app

## End goal
Combine interactive designs ~ Geogebra  
with vector graphics editor ~ Inkscape  
and specific type of animations ~ [Möbius transformations](https://en.wikipedia.org/wiki/M%C3%B6bius_transformation) and possibly [Lie sphere geometry](https://en.wikipedia.org/wiki/Lie_sphere_geometry)  

> [!NOTE]
> This project is in beta, beware of bugs and breaking changes

## Installation
[Live deployed here](https://pier-bezuhoff.github.io/Dodeclusters/) from the `github-pages` branch.  
Executables for Linux, Window and Android can be found in the assets [attached to the latest release](https://github.com/pier-bezuhoff/Dodeclusters/releases/) or [on Google Drive](https://drive.google.com/drive/folders/1abGxbUhnnr4mGyZERKv4ePH--us66Wd4?usp=sharing).

### Windows
1. From [the latest release](https://github.com/pier-bezuhoff/Dodeclusters/releases/tag/v0.3.0) go to `assets` and download `windows.zip`
2. Unarchive it
3. In folder `com.pierbezuhoff.dodeclusters` find file `com.pierbezuhoff.dodeclusters.exe`
4. Run it! That's it~ (it is bundled with JVM)

### Linux
1. From [the latest release](https://github.com/pier-bezuhoff/Dodeclusters/releases/) go to `assets` and download `linux.zip`
2. Unarchive it
3. Go into folder `com.pierbezuhoff.dodeclusters/bin`, you'll find executable file `com.pierbezuhoff.dodeclusters`
4. `chmod +x` it and you can run it as `./com.pierbezuhoff.dodeclusters` (bundled with JVM)

### Android
You can find the apk in the assets [attached to the latest release](https://github.com/pier-bezuhoff/Dodeclusters/releases/).

> [!NOTE]
> Bundled binaries are likely behind web version

## Keyboard shortcuts
- `Ctrl + A`: quickly select/deselect everything
- `Delete`, `Backspace`: delete selected objects
- `Ctrl + V`: duplicate selected objects
- `Ctrl + +`, `Ctrl + =`; `Ctrl + -`; mouse wheel: enlarge/shrink selected objects or zoom in/out
- `Ctrl + Z`: undo ↶
- `Ctrl + Y`: redo ↷
- `Esc`: cancel any ongoing constructions
- `M`: go to Drag mode (~ **M**ove)
- `S`: go to Multi**S**elect mode (~ **S**election)
- `R`: go to **R**egions mode
- `T`: open **T**ransform tools
- `C`: open **C**reate tools


## Build and run via Gradle

> [!IMPORTANT]
> Requires JDK 17 (later versions may work too)

### Run desktop app
`./gradlew :composeApp:run`  
### Run web app (dev)
`./gradlew :composeApp:wasmJsBrowserDevelopmentRun`  

### Package for Windows/macOS/Linux
Build platform-dependent package (e.g. run thru Github Actions using corresponding OS):  
`./gradlew composeApp:createDistributable`  
output directory: `composeApp/build/compose/binaries/main/app/`  
Same + use ProGuard to minify:  
`./gradlew composeApp:createReleaseDistributable`  
output directory: `composeApp/build/compose/binaries/main-release/app/`  

Individually:  
- `./gradlew packageReleaseMsi`  
- `./gradlew packageReleaseDmg` + notarize/register (requires Apple dev acc)  
- `./gradlew packageReleaseDeb`  
output directory: `composeApp/build/compose/binaries/main-release/app/`  

### Package for web browser 
`./gradlew wasmJsBrowserDistribution`  
output directory: `composeApp/build/dist/wasmJs/productionExecutable/`  

#### Package development version for web
`./gradlew wasmJsBrowserDevelopmentExecutableDistribution`
output directory: `composeApp/build/dist/wasmJs/developmentExecutable/`  

### Generate debug .apk for Android
`./gradlew assembleDebug`
output directory: `composeApp/build/outputs/apk/debug/`


## Web Wasm compatibility

To run applications built with Kotlin/Wasm in a browser, you need a browser supporting [wasm garbage collection feature](https://github.com/WebAssembly/gc):  
- For **Chrome** and **Chromium-based** browsers (Edge, Brave etc.), it **should just work** since version 119.
- For **Firefox** 120+ it **should just work**.
- For **Safari** it should work starting from version 18.2+.
> [!NOTE]
> Safari 18.2 is available for iOS 18.2, iPadOS 18.2, visionOS 2.2, macOS 15.2, macOS Sonoma, and macOS Ventura. On iOS and iPadOS, Safari 18.2 is bundled with the operating system. To get it, update your device to version 18.2 or later. [Safari release notes](https://developer.apple.com/documentation/safari-release-notes/safari-18_2-release-notes#Overview).
- [Relevant Wasm compatibility table.](https://webassembly.org/features/#table-row-gc)

For more information see https://kotl.in/wasm_help/.
