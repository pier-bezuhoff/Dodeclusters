# Dodeclusters
Create vector images using only intersections of circles  

This is a Kotlin Multiplatform project targeting Android, Web, Desktop.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.  

To run desktop:  
`./gradlew :composeApp:run`  
To run web app:  
`./gradlew :composeApp:wasmJsBrowserDevelopmentRun`  


To package for web browser:  
`./gradlew wasmJsBrowserDistribution`  
output goes into: `./composeApp/build/dist/wasmJs/productionExecutable/`  
can be deployed thru Github Pages  

To package for Windows/MacOs/Linux run thru Github Actions using corresponding OSs:  
`./gradlew packageReleaseMsi`  
`./gradlew packageReleaseDmg` + notarize/register  
`./gradlew packageReleaseDeb`  
output goes into: `./composeApp/build/compose/binaries/main-release/app/com.pierbezuhoff.dodeclusters/bin/`  
