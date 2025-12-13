import domain.model.ChangeHistory
import domain.model.SaveState
import domain.settings.Settings
import io.github.xxfast.kstore.KStore
import ui.editor.EditorViewModel

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

    // NOTE: LocalStorage allows 5 MB max
    @Deprecated("Migrate to SaveState")
    val lastStateStore: KStore<EditorViewModel.State>
    val settingsStore: KStore<Settings>
    val autosaveStore: KStore<SaveState>
    val historyStore: KStore<ChangeHistory.State>

    @Deprecated("Migrate to SaveState")
    fun saveLastState(state: EditorViewModel.State)
    fun saveSettings(settings: Settings)
    /** Use for autosave */
    fun saveState(state: SaveState)
    fun saveHistory(historyState: ChangeHistory.State)

    fun scrollToZoom(yDelta: Float): Float

    companion object {
        @Deprecated("Migrate to SaveState")
        const val LAST_STATE_STORE_FILE_NAME = "last-save"
        const val SETTINGS_STORE_FILE_NAME = "settings"
        const val AUTOSAVE_STORE_FILE_NAME = "autosave"
        const val HISTORY_STORE_FILE_NAME = "history"
    }
}

enum class PlatformKind {
    DESKTOP, ANDROID, WEB
}

expect fun getPlatform(): Platform
