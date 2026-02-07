package domain.io

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed interface SaveResult {
    val filename: String?
    val dir: String?

    val isSuccess: Boolean get() =
        this is Success

    @Serializable
    data class Success(
        override val filename: String,
        override val dir: String? = null,
    ) : SaveResult

    @Serializable
    data class Cancelled(
        override val filename: String? = null,
        override val dir: String? = null,
    ) : SaveResult

    @Serializable
    data class Failure(
        override val filename: String? = null,
        override val dir: String? = null,
        val error: String? = null,
    ) : SaveResult
}