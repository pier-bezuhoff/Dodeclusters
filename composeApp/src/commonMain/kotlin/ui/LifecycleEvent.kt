package ui

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class LifecycleEvent {
    /** signals to save UI state */
    SaveUIState,
}