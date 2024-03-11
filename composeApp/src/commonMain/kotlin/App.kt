import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import ui.EditClusterScreen

@Composable
fun App(
    sampleIndex: Int? = null,
    ddcContent: String? = null,
) {
    MaterialTheme {
        EditClusterScreen(
            sampleIndex = sampleIndex,
            ddcContent = ddcContent
        )
    }
}