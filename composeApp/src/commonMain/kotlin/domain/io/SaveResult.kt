package domain.io

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed interface SaveResult {
    val filename: String?
    val directory: String?
    val uri: String?

    val isSuccess: Boolean get() =
        this is Success

    @Serializable
    data class Success(
        override val filename: String,
        override val directory: String? = null,
        override val uri: String? = null,
    ) : SaveResult

    @Serializable
    data class Cancelled(
        override val filename: String? = null,
        override val directory: String? = null,
    ) : SaveResult {
        override val uri: String? = null
    }

    @Serializable
    data class Failure(
        override val filename: String? = null,
        override val directory: String? = null,
        val error: String? = null,
    ) : SaveResult {
        override val uri: String? = null
    }

    fun asSaveConfig(): SaveConfig =
        SaveConfig(
            name = filename?.substringBeforeLast('.'),
            directory = directory,
            uri = uri,
        )
}