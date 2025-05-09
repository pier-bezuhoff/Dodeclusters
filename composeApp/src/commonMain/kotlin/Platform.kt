import domain.Settings
import io.github.xxfast.kstore.KStore
import ui.edit_cluster.EditClusterViewModel

val MIN_CIRCLE_TO_CUBIC_APPROXIMATION_RADIUS: Float =
    getPlatform().minCircleToCubicApproximationRadius
val MIN_CIRCLE_TO_LINE_APPROXIMATION_RADIUS: Float =
    getPlatform().minCircleToLineApproximationRadius

interface Platform {
    val name: String
    val kind: PlatformKind
    val fileSeparator: Char
    /** min tap/grab distance to select an object in dp */
    val tapRadius: Float
    /** Circles with radius larger than [minCircleToCubicApproximationRadius] will be
     * approximated by cubic bezier on screen + lines off screen */
    val minCircleToCubicApproximationRadius: Float
    /** Circles with radius larger than [minCircleToLineApproximationRadius] will be approximated by lines */
    val minCircleToLineApproximationRadius: Float
    val lastStateStore: KStore<EditClusterViewModel.State>
    val settingsStore: KStore<Settings>
    fun saveLastState(state: EditClusterViewModel.State)
    fun saveSettings(settings: Settings)
    // MAYBE: also save history (to a separate store) for UX
    //  tho be aware that LocalStorage allows 5 MB max
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
