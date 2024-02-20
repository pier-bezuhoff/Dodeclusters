import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.EditClusterScreen

@Composable
fun App(
    usingMobileBrowser: Boolean = false,
    sampleIndex: Int? = null,
    ddcContent: String? = null,
) {
    MaterialTheme {
        if (usingMobileBrowser) {
            Column(Modifier.fillMaxHeight()) {
                Text("Dodeclusters is not compatible with mobile browsers yet," +
                        "consider installing it from an .apk for Android:" +
                        "https://drive.google.com/drive/folders/1abGxbUhnnr4mGyZERKv4ePH--us66Wd4")
                EditClusterScreen(sampleIndex, ddcContent = ddcContent)
            }
        } else EditClusterScreen(sampleIndex, ddcContent = ddcContent)
    }
}