import android.os.Build
import domain.model.ChangeHistory
import domain.model.SaveState
import domain.settings.Settings
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

object AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val kind: PlatformKind = PlatformKind.ANDROID
    override val fileSeparator: Char = '/'
    override val tapRadius: Float = 15f
    // NOTE: cubic approx is performing remarkably bad on Android [my old tablet]
    override val minCircleToCubicApproximationRadius: Float = 7_000f
    override val minCircleToLineApproximationRadius: Float = 7_000f

    lateinit var filesDir: Path

    @Deprecated("Migrate to SaveState")
    override val lastStateStore: KStore<EditorViewModel.State> by lazy {
        storeOf(
            file = Path(filesDir, Platform.LAST_STATE_STORE_FILE_NAME + ".json"),
            json = EditorViewModel.State.JSON_FORMAT,
        )
    }
    override val settingsStore: KStore<Settings> by lazy {
        storeOf(
            file = Path(filesDir, Platform.SETTINGS_STORE_FILE_NAME + ".json"),
            json = Settings.JSON_FORMAT,
        )
    }
    override val autosaveStore: KStore<SaveState> by lazy {
        storeOf(
            file = Path(filesDir, Platform.AUTOSAVE_STORE_FILE_NAME + ".json"),
            json = SaveState.JSON_FORMAT,
        )
    }
    override val historyStore: KStore<ChangeHistory.State> by lazy {
        storeOf(
            file = Path(filesDir, Platform.HISTORY_STORE_FILE_NAME + ".json"),
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

    @Deprecated("Migrate to SaveState")
    @OptIn(DelicateCoroutinesApi::class)
    override fun saveLastState(state: EditorViewModel.State) =
        lastStateStore.save(state)

    @OptIn(DelicateCoroutinesApi::class)
    override fun saveSettings(settings: Settings) =
        settingsStore.save(settings)

    @OptIn(DelicateCoroutinesApi::class)
    override fun saveState(state: SaveState) =
        autosaveStore.save(state)

    @OptIn(DelicateCoroutinesApi::class)
    override fun saveHistory(historyState: ChangeHistory.State) =
        historyStore.save(historyState)

    // since this is triggered by mouse scroll, it is irrelevant to android
    override fun scrollToZoom(yDelta: Float): Float {
        val zoom = (1.01f).pow(-yDelta)
        return zoom
    }
}

actual fun getPlatform(): Platform = AndroidPlatform

fun setFilesDir(filesDir: File) {
    AndroidPlatform.filesDir = Path(filesDir.absolutePath)
}