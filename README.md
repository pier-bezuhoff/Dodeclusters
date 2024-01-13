# Dodeclusters
Create vector images using only intersections of circles  
[Deployed here](https://pier-bezuhoff.github.io/Dodeclusters/) from the `github-pages` branch  

## Project structure
This is a Kotlin Multiplatform project targeting Android, Web, Desktop.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.  

## Common run/build commands
To run desktop:  
`./gradlew :composeApp:run`  
To run web app:  
`./gradlew :composeApp:wasmJsBrowserDevelopmentRun`  


To package for web browser:  
`./gradlew wasmJsBrowserDistribution`  
output goes into: `./composeApp/build/dist/wasmJs/productionExecutable/`  
and can be deployed thru Github Pages  

To package for Windows/MacOs/Linux run thru Github Actions using corresponding OSs:  
`./gradlew packageReleaseMsi`  
`./gradlew packageReleaseDmg` + notarize/register  
`./gradlew packageReleaseDeb`  
output goes into: `./composeApp/build/compose/binaries/main-release/app/com.pierbezuhoff.dodeclusters/bin/`  

## TODO
* Cluster editor
  - drag, select, copy, create & delete circles
  - move & scale circles
  - select/deselect intersection regions
  - bezier-like pathing tool
  - maybe finer control over angles/distances
* Multi-cluster editor
  - drag, select, copy, create & delete clusters
  - move, scale & rotate clusters
  - change color, border color, fill/wireframe
  - re-order / sidebar layer-like interface
* New cluster-based format to inherit from `.ddu` (e.g. `.ddo`)
  - probs `.yaml` based
  - centered to (0,0)
  - cluster numbering & separate circle numbering
* other actions
  - read `.ddu`
  - maybe save as `.svg`
