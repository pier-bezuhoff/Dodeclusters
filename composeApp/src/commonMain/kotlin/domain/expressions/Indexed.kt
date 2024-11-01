package domain.expressions

import androidx.compose.runtime.Immutable
import domain.Ix
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// potential optimization: represent point indices as
// -(i+1) while circle indices are +i
@Serializable
@Immutable
sealed interface Indexed {
    val index: Ix

    /** index for a circle or a line */
    @Serializable
    @SerialName("CircleWithIndex")
    data class Circle(override val index: Ix) : Indexed
    @Serializable
    @SerialName("PointWithIndex")
    data class Point(override val index: Ix) : Indexed
}