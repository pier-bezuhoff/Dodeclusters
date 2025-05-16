package domain.io

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.flow.SharedFlow

// MAYBE: make it less platform-specific
@Immutable
data class LookupData(
    // no leading dot
    val extensions: Set<String>, // used for desktop
    val htmlFileInputAccept: String, // accept attribute of <input>
    val androidMimeType: String,
) {
    companion object {
        val YAML = LookupData(
            extensions = setOf("yaml", "yml", "ddc", "json"),
            htmlFileInputAccept = ".ddc, .yml, .yaml, .json|application/yaml, application/json",
            // "application/yaml" is no good for some normal .yml's (idk why)
            // BUG: "application/*" does not accept *small* yaml files in
            //  *some* file pickers (e.g. MIUI's default one), it thinks they are text/* or smth...
            // NOTE: i know whom to blame, it only took 22 years... https://www.rfc-editor.org/rfc/rfc9512.html
            androidMimeType = "application/*"
        )
    }
}

/** Open text file button */
@Composable
expect fun OpenFileButton(
    iconPainter: Painter,
    contentDescription: String,
    lookupData: LookupData,
    modifier: Modifier = Modifier,
    openRequests: SharedFlow<Unit>? = null,
    onOpen: (content: String?) -> Unit
)