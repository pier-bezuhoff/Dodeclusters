import domain.settings.Settings
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
    /**
     * Circles with [minCircleToLineApproximationRadius] > radius >=
     * [minCircleToCubicApproximationRadius] will be approximated by cubic bezier on screen +
     * lines off screen. Cubic bezier approximation is
     * worse quality-wise than no-approx for smaller (R<=10k) circles but faster;
     * for larger circles it's visually better than non-approx, or line approximation.
     * Slower than line approximation (especially on [my old] Android).
     *
     * [minCircleToCubicApproximationRadius] <= [minCircleToLineApproximationRadius]
     */
    val minCircleToCubicApproximationRadius: Float
    /**
     * Circles with radius >= [minCircleToLineApproximationRadius] will be
     * approximated by lines. Lower values improve performance, but sacrifice display quality.
     *
     * [minCircleToCubicApproximationRadius] <= [minCircleToLineApproximationRadius]
     */
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
