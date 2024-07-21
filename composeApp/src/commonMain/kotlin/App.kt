import androidx.compose.runtime.Composable
import ui.edit_cluster.EditClusterScreen
import ui.theme.ColorTheme
import ui.theme.DEFAULT_COLOR_THEME
import ui.theme.DodeclustersTheme

@Composable
fun App(
    sampleIndex: Int? = null,
    ddcContent: String? = null,
    colorTheme: ColorTheme = DEFAULT_COLOR_THEME,
) {
    DodeclustersTheme(colorTheme) {
        EditClusterScreen(
            sampleIndex = sampleIndex,
            ddcContent = ddcContent
        )
    }
}