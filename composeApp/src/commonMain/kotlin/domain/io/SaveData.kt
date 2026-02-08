package domain.io

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
/** @param[T] result type of [prepareContent] */
data class SaveData<T>(
    /** file's name without extension */
    val name: String,
    /** no leading dot, empty string if no extension */
    val extension: String,
    val lastDir: String? = null,
    /** used for overwriting on Android */
    val uri: String? = null,
    val otherDisplayedExtensions: Set<String> = emptySet(),
    val mimeType: String = DEFAULT_MIME_TYPE,
    val prepareContent: suspend (name: String) -> T,
) {
    constructor(
        filename: String,
        lastDir: String? = null,
        uri: String? = null,
        otherDisplayedExtensions: Set<String> = emptySet(),
        mimeType: String = DEFAULT_MIME_TYPE,
        prepareContent: suspend (name: String) -> T,
    ) : this(
        name = filename.substringBeforeLast('.', missingDelimiterValue = filename),
        extension = filename.substringAfterLast('.', missingDelimiterValue = ""),
        lastDir = lastDir,
        uri = uri,
        otherDisplayedExtensions = otherDisplayedExtensions,
        mimeType = mimeType,
        prepareContent = prepareContent,
    )

    val filename: String =
        if (extension.isBlank()) name
        else "$name.$extension"

    companion object {
        const val DEFAULT_MIME_TYPE = "*/*"
    }
}