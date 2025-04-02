import domain.Settings
import domain.io.getAppDataDir
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import kotlinx.io.files.Path
import ui.edit_cluster.EditClusterViewModel
import java.io.File
import kotlin.math.pow

object JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val kind: PlatformKind = PlatformKind.DESKTOP
    override val fileSeparator: Char = File.separatorChar
    override val tapRadius: Float = 10f
    override val maxCircleRadius: Float = 1e5f
    private val dataDir: Path by lazy { getAppDataDir() }
    override val lastStateStore: KStore<EditClusterViewModel.State> by lazy {
        storeOf(
            file = Path(dataDir, Platform.LAST_STATE_STORE_FILE_NAME + ".json"),
            json = EditClusterViewModel.State.JSON_FORMAT,
        )
    }
    override val settingsStore: KStore<Settings> by lazy {
        storeOf(
            file = Path(dataDir, Platform.SETTINGS_STORE_FILE_NAME + ".json"),
            json = Settings.JSON_FORMAT,
        )
    }

    // reference: https://stackoverflow.com/a/75734381/7143065
    @OptIn(DelicateCoroutinesApi::class)
    override fun saveLastState(state: EditClusterViewModel.State) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                lastStateStore.set(state)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun saveSettings(settings: Settings) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                settingsStore.set(settings)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
    override fun scrollToZoom(yDelta: Float): Float {
        val percent = 2.5f
        val zoom = (1f + percent/100f).pow(-yDelta)
        return zoom
    }
}

actual fun getPlatform(): Platform = JVMPlatform