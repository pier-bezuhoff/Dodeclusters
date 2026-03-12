package ui.editor

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Type of handles used to manipulate selected objects */
@Immutable
@Serializable
enum class HandleConfig {
    SINGLE_CIRCLE,
    SEVERAL_OBJECTS,
}