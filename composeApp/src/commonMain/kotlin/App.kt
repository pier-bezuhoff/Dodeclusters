import androidx.compose.runtime.Composable
import ui.edit_cluster.EditClusterScreen
import ui.theme.DodeclustersTheme

@Composable
fun App(
    sampleIndex: Int? = null,
    ddcContent: String? = null,
) {
    DodeclustersTheme {
        EditClusterScreen(
            sampleIndex = sampleIndex,
            ddcContent = ddcContent
        )
    }
}