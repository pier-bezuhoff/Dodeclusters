# Dodeclusters
[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/pier-bezuhoff/Dodeclusters/build.yml?branch=master&event=push)](https://github.com/pier-bezuhoff/Dodeclusters/actions)  

Conformal vector image editor: using only circles (WIP)  
<insert 0-inf banner here>  
Built with [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform), targeting
- [x] Desktop (Windows, Linux)
- [x] Android 7.0+ (SDK 24+)
- [x] Web (via [Wasm](#Web-Wasm-compatibility))
- [ ] macOS/iOS if ever get an Apple dev account (have to notarize/staple binaries), for now only works as a web app thru Chrome/Firefox

## End goal
Combine interactive designs ~ Geogebra  
with graphical vector editor ~ Inkscape  
and specific type of animations ~ [Möbius transformations](https://en.wikipedia.org/wiki/M%C3%B6bius_transformation) and possibly [Lie sphere geometry](https://en.wikipedia.org/wiki/Lie_sphere_geometry)  

## Installation
[Live deployed here](https://pier-bezuhoff.github.io/Dodeclusters/) from the `github-pages` branch  
Binaries for Linux, Window and Android are stored [here](https://drive.google.com/drive/folders/1abGxbUhnnr4mGyZERKv4ePH--us66Wd4?usp=drive_link).


## Roadmap:

Cluster = circles + union of intersections of some of these circles (their insides or outsides)  

* Cluster editor
  - [x] drag, select, copy, create & delete circles
  - [x] move & scale circles
  - [x] select/deselect intersection regions
  - [x] fill regions with diff colors
  - [x] select binary interlacing even/odd regions (like chessboard coloring)
  - [x] load/save individual clusters as .ddc (actually json inside, temporary solution)
  - [ ] additional context-dependent toolbar to group categories of tools (a-la bucket fill + gradient)
  - [x] tools: circle by center & radius, by 3 points; maybe line by 2 points
  - [ ] bezier-like pathing tool
  - [ ] maybe finer control over angles/distances
* Multi-cluster editor
  - drag, select, copy, create & delete clusters
  - move, scale & rotate clusters
  - change color, border color, fill/wireframe
  - re-order / sidebar layer-like interface
  - create and apply Möbius transforms as animations
* Other
  - [x] history: undo + redo
  - [x] New cluster-based format to supercede `.ddu`, YAML subset
  - [ ] read `.ddu`
  - [ ] maybe export as `.svg`
  - [ ] maybe add up/down/left/right arrow controls to temporarily fix the mobile browser issue


## Build and run via Gradle

### Run desktop app
`./gradlew :composeApp:run`  
### Run web app (dev)
`./gradlew :composeApp:wasmJsBrowserDevelopmentRun`  

### Package for Windows/macOS/Linux
Build platform-dependent package (run thru Github Actions using corresponding OS):  
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

### Generate debug .apk for Android
`./gradlew assembleDebug`
output directory: `composeApp/build/outputs/apk/debug/`


## Web Wasm compatibility

To run applications built with Kotlin/Wasm in a browser, you need a browser supporting [wasm garbage collection feature](https://github.com/WebAssembly/gc):  
- For **Chrome** and **Chromium-based** browsers (Edge, Brave etc.), it **should just work** since version 119.
- For **Firefox** 120+ it **should just work**.
- For **Firefox** 119:
  1. Open `about:config` in the browser.
  2. Enable **javascript.options.wasm_gc**.
  3. Refresh the page.  
- For **Safari** it is _NOT_ implemented as of now (March 2024)

For more information see https://kotl.in/wasm_help/.
