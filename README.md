# Dodeclusters
Vector image editor using only circles (WIP)  
Built with [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform)

[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/pier-bezuhoff/Dodeclusters/build.yml?branch=master&event=push)](https://github.com/pier-bezuhoff/Dodeclusters/actions)  
[Deployed here](https://pier-bezuhoff.github.io/Dodeclusters/) from the `github-pages` branch  

## TODO:
* Cluster editor
  - cluster = circles + union of intersections of some of the circles
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
  - maybe export as `.svg`

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

