import domain.Settings
import io.github.xxfast.kstore.KStore
import ui.edit_cluster.EditClusterViewModel

interface Platform {
    val name: String
    val kind: PlatformKind
    val fileSeparator: Char
    /** min tap/grab distance to select an object in dp */
    val tapRadius: Float
    /** Circles with radius larger than [maxCircleRadius] will be approximated by lines */
    val maxCircleRadius: Float
    val lastStateStore: KStore<EditClusterViewModel.State>
    val settingsStore: KStore<Settings>
    fun saveLastState(state: EditClusterViewModel.State)
    fun saveSettings(settings: Settings)
    // MAYBE: also save history (to a separate store) for UX
    fun scrollToZoom(yDelta: Float): Float

    companion object {
        const val LAST_STATE_STORE_FILE_NAME = "last-save"
        const val SETTINGS_STORE_FILE_NAME = "settings"
    }
}

enum class PlatformKind {
    DESKTOP, ANDROID, WEB
}

expect fun getPlatform(): Platform
