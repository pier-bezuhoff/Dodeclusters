import domain.settings.Settings
import domain.io.getAppDataDir
import domain.model.ChangeHistory
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.serialization.Serializable
import ui.editor.EditorViewModel
import java.io.File
import kotlin.math.pow

object JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val kind: PlatformKind = PlatformKind.DESKTOP
    override val fileSeparator: Char = File.separatorChar
    override val tapRadius: Float = 10f
    override val minCircleToCubicApproximationRadius: Float = 10_000f
    override val minCircleToLineApproximationRadius: Float = 100_000f

    private val dataDir: Path by lazy { getAppDataDir() }

    override val lastStateStore: KStore<EditorViewModel.State> by lazy {
        storeOf(
            file = Path(dataDir, Platform.LAST_STATE_STORE_FILE_NAME + ".json"),
            json = EditorViewModel.State.JSON_FORMAT,
        )
    }
    override val settingsStore: KStore<Settings> by lazy {
        storeOf(
            file = Path(dataDir, Platform.SETTINGS_STORE_FILE_NAME + ".json"),
            json = Settings.JSON_FORMAT,
        )
    }
    override val historyStore: KStore<ChangeHistory.State> by lazy {
        storeOf(
            file = Path(dataDir, Platform.HISTORY_STORE_FILE_NAME + ".json"),
            json = ChangeHistory.State.JSON_FORMAT,
        )
    }

    // reference: https://stackoverflow.com/a/75734381/7143065
    @OptIn(DelicateCoroutinesApi::class)
    private inline fun <reified T : @Serializable Any> KStore<T>.save(value: T) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                this@save.set(value)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun saveLastState(state: EditorViewModel.State) {
        lastStateStore.save(state)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun saveSettings(settings: Settings) {
        settingsStore.save(settings)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun saveHistory(historyState: ChangeHistory.State) {
        historyStore.save(historyState)
    }

    override fun scrollToZoom(yDelta: Float): Float {
        val percent = 2.5f
        val zoom = (1f + percent/100f).pow(-yDelta)
        return zoom
    }
}

actual fun getPlatform(): Platform = JVMPlatform