package domain.io

import ca.gosyer.appdirs.AppDirs
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

fun getAppDataDir(): Path {
    val appDirs = AppDirs(
        appName = "Dodeclusters",
        appAuthor = "pier-bezuhoff",
    )
    // on Linux this is ~/.local/share/$appName
    val dataDir = Path(appDirs.getUserDataDir())
    with (SystemFileSystem) {
        if(!exists(dataDir))
            createDirectories(dataDir)
    }
    return dataDir
}
