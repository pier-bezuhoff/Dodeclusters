package domain.model

import androidx.compose.ui.geometry.Offset
import domain.ColorAsCss
import domain.SerializableOffset
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * Each object can have its own Styling object
 * @param[labelPositionShift] shift from default label position
 */
@Serializable
data class Styling(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val borderColor: ColorAsCss? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val fillColor: ColorAsCss? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val isPhantom: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val label: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val labelPositionShift: SerializableOffset = Offset.Zero,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val lineThickness: Int? = null,
)
