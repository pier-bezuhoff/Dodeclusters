# Dodeclusters
[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/pier-bezuhoff/Dodeclusters/build.yml?branch=master&event=push)](https://github.com/pier-bezuhoff/Dodeclusters/actions)  

### Conformal vector graphics editor: treating points, circles and straight lines equally

![Dodeclusters](docs/02inf-art.png)

Built with [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform), targeting
- [x] Desktop (Windows, Linux)
- [x] Android 7.0+ (SDK 24+)
- [x] Web (via [Wasm](#Web-Wasm-compatibility))
- [ ] macOS/iOS if ever get an Apple dev account (have to notarize/staple binaries), for now 
  only works as a web app thru Chrome/Firefox

## End goal
Combine interactive designs ~ Geogebra  
with vector graphics editor ~ Inkscape  
and specific type of animations ~ [Möbius transformations](https://en.wikipedia.org/wiki/M%C3%B6bius_transformation) and possibly [Lie sphere geometry](https://en.wikipedia.org/wiki/Lie_sphere_geometry)  

> [!NOTE]
> This project is in beta

## Installation
[Live deployed here](https://pier-bezuhoff.github.io/Dodeclusters/) from the `github-pages` branch.  
Executables for Linux, Window and Android can be found in the assets [attached to the latest release](https://github.com/pier-bezuhoff/Dodeclusters/releases/).

### Windows
1. From [the latest release](https://github.com/pier-bezuhoff/Dodeclusters/releases/tag/v0.2.0) go to `assets` and download `windows.zip`
2. Unarchive it
3. In folder `com.pierbezuhoff.dodeclusters` find file `com.pierbezuhoff.dodeclusters.exe`
4. Run it! That's it~ (it is bundled with JVM)

### Linux
1. From [the latest release](https://github.com/pier-bezuhoff/Dodeclusters/releases/) go to `assets` and download `linux.zip`
2. Unarchive it
3. Go into folder `com.pierbezuhoff.dodeclusters/bin`, you'll find executable file `com.pierbezuhoff.dodeclusters`
4. `chmod +x` it and you can run it as `./com.pierbezuhoff.dodeclusters`

### Android
You can find the apk in the assets [attached to the latest release](https://github.com/pier-bezuhoff/Dodeclusters/releases/).

> [!NOTE]
> Bundled binaries are likely behind the web version

## Keyboard shortcuts
- `Ctrl + A`: quickly select/deselect everything
- `Delete`, `Backspace`: delete selected objects
- `Ctrl + V`: duplicate selected objects
- `Ctrl + +`, `Ctrl + =`; `Ctrl + -`; mouse wheel: enlarge/shrink selected objects
- `Ctrl + Z`: undo
- `Ctrl + Y`: redo
- `Esc`: cancel any ongoing constructions


## Build and run via Gradle

> [!IMPORTANT]
> Requires JDK 17

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
