package domain.io

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable


/**
 * @param[uri] used for overwriting on Android
 */
@Immutable
@Serializable
data class SaveConfig(
    val name: String? = null,
    // TODO: persist directory
    val directory: String? = null,
    val uri: String? = null,
)
