import io.github.xxfast.kstore.KStore
import ui.edit_cluster.EditClusterViewModel

interface Platform {
    val name: String
    val fileSeparator: Char
    /** min tap/grab distance to select an object in dp */
    val tapRadius: Float
    /** Circles with radius larger than [maxCircleRadius] will be approximated by lines */
    val maxCircleRadius: Float
    val lastStateStore: KStore<EditClusterViewModel.State>
    fun saveLastState(state: EditClusterViewModel.State)
    fun scrollToZoom(yDelta: Float): Float

    companion object {
        const val LAST_STATE_STORE_FILE_NAME = "last-save"
    }
}

expect fun getPlatform(): Platform
