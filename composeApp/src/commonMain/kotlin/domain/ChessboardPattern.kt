package domain

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class ChessboardPattern {
    NONE,
    STARTS_COLORED,
    STARTS_TRANSPARENT
}

